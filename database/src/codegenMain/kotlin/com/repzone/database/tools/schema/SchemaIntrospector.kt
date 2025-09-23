package com.repzone.database.tools.schema

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

private data class Col(
    val name: String,
    val type: String?,
    val notNull: Boolean,
    val dflt: String?,
    val pkOrder: Int,
    val autoInc: Boolean
)

private data class Tbl(
    val name: String,
    val cols: List<Col>
)

fun main(args: Array<String>) {
    val params = args.associate {
        val (k, v) = it.removePrefix("--").split("=", limit = 2)
        k to v
    }
    val sqRoot = File(params["sqldelightRoot"] ?: error("Missing --sqldelightRoot"))
    val outDir = File(params["out"] ?: error("Missing --out"))
    val pkg = params["pkg"] ?: error("Missing --pkg")

    require(sqRoot.exists()) { "sqldelight root not found: $sqRoot" }
    outDir.mkdirs()

    println("[orm-meta] sqRoot=$sqRoot")
    println("[orm-meta] outDir=$outDir")
    println("[orm-meta] pkg=$pkg")

    Class.forName("org.sqlite.JDBC")
    DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
        conn.createStatement().use { st -> st.execute("PRAGMA foreign_keys = ON;") }

        // 1) Tüm .sq/.sqm/.sql dosyalarını topla
        val allFiles = sqRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("sq", "sqm", "sql") }
            .toList()
            .sortedBy { it.absolutePath }

        println("[orm-meta] found ${allFiles.size} sql files")

        // 2) DDL’leri çıkar (CREATE/ALTER/INDEX), migration dosyaları doğal sıralamada
        val ddl = extractDdl(allFiles)
        println("[orm-meta] extracted ${ddl.size} DDL statements")

        // 3) In-memory DB’ye uygula
        applySql(conn, ddl)

        // 4) PRAGMA ile tabloları yükle
        val tables = loadTables(conn)
        println("[orm-meta] loaded ${tables.size} tables from in-memory DB")

        // 5) Kotlin kaynaklarını yaz
        val file = File(outDir, "GeneratedMeta.kt")
        file.parentFile.mkdirs()
        file.writeText(renderKotlin(pkg, tables))
        println("[orm-meta] Generated: ${file.absolutePath} (tables: ${tables.size})")
    }
}

private fun extractDdl(files: List<File>): List<String> {
    val ddl = mutableListOf<String>()
    files.forEach { f ->
        val text = f.readText()
        // kaba ayırma: ; ile böl, CREATE/ALTER/INDEX'leri al
        text.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { s ->
                val head = s.take(64).lowercase() // 32 → 64'e çıkardık; başına yorum gelirse de yakalasın
                head.contains("create table") ||
                        head.contains("create index") ||
                        head.contains("alter table")
            }
            .forEach { ddl += it + ";" }
    }
    return ddl
}

private fun applySql(conn: Connection, ddl: List<String>) {
    conn.createStatement().use { st ->
        ddl.forEachIndexed { idx, sql ->
            try {
                st.execute(sql)
            } catch (e: Exception) {
                println("[orm-meta][warn] failed to apply DDL #$idx: ${e.message}")
            }
        }
    }
}

private fun loadTables(conn: Connection): List<Tbl> {
    val list = mutableListOf<Tbl>()
    val autoIncTables = mutableSetOf<String>()

    conn.createStatement().use { st ->
        // AUTOINCREMENT ipucunu sqlite_master’dan çek
        st.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rs ->
            while (rs.next()) {
                val tname = rs.getString(1)
                val sql = rs.getString(2) ?: ""
                if (sql.contains("AUTOINCREMENT", ignoreCase = true)) {
                    autoIncTables += tname
                }
            }
        }
    }

    conn.createStatement().use { st ->
        st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { rs2 ->
            val names = mutableListOf<String>()
            while (rs2.next()) names += rs2.getString(1)

            names.forEach { t ->
                val cols = mutableListOf<Col>()
                conn.createStatement().use { st2 ->
                    st2.executeQuery("PRAGMA table_info('$t')").use { rs3 ->
                        while (rs3.next()) {
                            val name = rs3.getString("name")
                            val type = rs3.getString("type")
                            val notNull = rs3.getInt("notnull") == 1
                            val dflt = rs3.getString("dflt_value")
                            val pk = rs3.getInt("pk") // 0=pk değil, >0=pk sırası
                            cols += Col(
                                name = name,
                                type = type,
                                notNull = notNull,
                                dflt = dflt,
                                pkOrder = pk,
                                autoInc = (name.equals("id", true) && t in autoIncTables)
                            )
                        }
                    }
                }
                list += Tbl(t, cols)
            }
        }
    }

    return list
}

private fun toAffinity(t: String?): String = when {
    t == null -> "UNKNOWN"
    t.contains("INT", true) -> "INTEGER"
    t.contains("CHAR", true) || t.contains("CLOB", true) || t.contains("TEXT", true) -> "TEXT"
    t.contains("BLOB", true) -> "BLOB"
    t.contains("REAL", true) || t.contains("FLOA", true) || t.contains("DOUB", true) -> "REAL"
    else -> "NUMERIC"
}

private fun renderKotlin(pkg: String, tables: List<Tbl>): String = buildString {
    appendLine("package $pkg")
    appendLine()
    appendLine("import com.repzone.orm.meta.ColumnAffinity")
    appendLine("import com.repzone.orm.meta.ColumnMeta")
    appendLine("import com.repzone.orm.meta.TableMeta")
    appendLine("import com.repzone.orm.registry.OrmRegistry")
    appendLine()
    appendLine("// AUTO-GENERATED. DO NOT EDIT.")
    appendLine("object GeneratedTables {")
    tables.forEach { t ->
        val typeId = t.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        appendLine("  val ${t.name} = TableMeta(")
        appendLine("    tableName = ${qt(t.name)},")
        appendLine("    typeId = ${qt(typeId)},")
        appendLine("    columns = listOf(")
        t.cols.forEachIndexed { i, c ->
            val comma = if (i == t.cols.lastIndex) "" else ","
            appendLine("      ColumnMeta(" +
                    "name=${qt(c.name)}," +
                    "affinity=ColumnAffinity.${toAffinity(c.type)}," +
                    "nullable=${(!c.notNull)}," +
                    "defaultValueSql=${qtn(c.dflt)}," +
                    "primaryKeyOrder=${c.pkOrder}," +
                    "autoIncrement=${c.autoInc}" +
                    ")$comma")
        }
        appendLine("    )")
        appendLine("  )")
    }
    appendLine("}")
    appendLine()
    appendLine("object OrmRegistryImpl : OrmRegistry {")
    appendLine("  override val tables: List<TableMeta> = listOf(")
    tables.forEachIndexed { i, t ->
        val comma = if (i == tables.lastIndex) "" else ","
        appendLine("    GeneratedTables.${t.name}$comma")
    }
    appendLine("  )")
    appendLine("}")
}

private fun qt(s: String) = "\"" + s.replace("\"", "\\\"") + "\""
private fun qtn(s: String?) = s?.let { qt(it) } ?: "null"
