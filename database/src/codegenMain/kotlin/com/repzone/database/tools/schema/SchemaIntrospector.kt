package com.repzone.database.tools.schema

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

// ------ İç veri modelleri ------
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

// ------ Entry point ------
fun main(args: Array<String>) {
    val params = args.associate {
        val (k, v) = it.removePrefix("--").split("=", limit = 2)
        k to v
    }
    val sqRoot = File(params["sqldelightRoot"] ?: error("Missing --sqldelightRoot"))
    val outDir = File(params["out"] ?: error("Missing --out"))
    val pkg    = params["pkg"] ?: error("Missing --pkg")
    val sqPkg  = params["sqPkg"] ?: "com.repzone.database"

    require(sqRoot.exists()) { "sqldelight root not found: $sqRoot" }
    outDir.mkdirs()

    println("[orm-meta] sqRoot=$sqRoot")
    println("[orm-meta] outDir=$outDir")
    println("[orm-meta] pkg=$pkg")
    println("[orm-meta] sqPkg=$sqPkg")

    Class.forName("org.sqlite.JDBC")
    DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
        conn.createStatement().use { st -> st.execute("PRAGMA foreign_keys = ON;") }

        // 1) .sq/.sqm/.sql dosyalarını topla (schema + migrations)
        val allFiles = sqRoot.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("sq", "sqm", "sql") }
            .toList()
            .sortedBy { it.absolutePath } // deterministik sıra

        println("[orm-meta] found ${allFiles.size} sql files")

        // 2) DDL çıkar (CREATE/ALTER/INDEX)
        val ddl = extractDdl(allFiles)
        println("[orm-meta] extracted ${ddl.size} DDL statements")

        // 3) In-memory DB’ye uygula
        applySql(conn, ddl)

        // 4) PRAGMA üzerinden tablo/kolon meta bilgilerini yükle
        val tables = loadTables(conn)
        println("[orm-meta] loaded ${tables.size} tables from in-memory DB")

        // 5) Kotlin kodu yaz
        val file = File(outDir, "GeneratedMeta.kt")
        file.parentFile.mkdirs()
        file.writeText(renderKotlin(pkg, tables, sqPkg))
        println("[orm-meta] Generated: ${file.absolutePath} (tables: ${tables.size})")
    }
}

// ------ Yardımcılar (DDL) ------
private fun extractDdl(files: List<File>): List<String> {
    val ddl = mutableListOf<String>()
    files.forEach { f ->
        val raw = f.readText()
        val text = stripComments(raw)
        // Noktalı virgül ile kaba split, CREATE/ALTER/INDEX yakala
        text.split(';')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { s ->
                val head = s.take(128).lowercase()
                head.contains("create table") ||
                        head.contains("create index") ||
                        head.contains("alter table")
            }
            .forEach { ddl += "$it;" }
    }
    return ddl
}

private fun stripComments(input: String): String {
    // /* ... */ blok yorumlarını kaldır
    val noBlock = input.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
    // -- satır sonuna kadar yorumları kaldır
    return noBlock.lines().joinToString("\n") { line ->
        val i = line.indexOf("--")
        if (i >= 0) line.substring(0, i) else line
    }
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

// ------ PRAGMA okuyucular ------
private fun loadTables(conn: Connection): List<Tbl> {
    val list = mutableListOf<Tbl>()
    val autoIncTables = mutableSetOf<String>()

    conn.createStatement().use { st ->
        st.executeQuery(
            "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ).use { rs ->
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
        st.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ).use { rs2 ->
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

// ------ Kod üretimi ------
private fun toAffinity(t: String?): String = when {
    t == null -> "UNKNOWN"
    t.contains("INT", true) -> "INTEGER"
    t.contains("CHAR", true) || t.contains("CLOB", true) || t.contains("TEXT", true) -> "TEXT"
    t.contains("BLOB", true) -> "BLOB"
    t.contains("REAL", true) || t.contains("FLOA", true) || t.contains("DOUB", true) -> "REAL"
    else -> "NUMERIC"
}

private fun kotlinTypeFromAffinity(affinity: String, nullable: Boolean): String {
    val base = when (affinity) {
        "INTEGER" -> "Long"
        "REAL"    -> "Double"
        "TEXT"    -> "String"
        "BLOB"    -> "ByteArray"
        "NUMERIC","UNKNOWN" -> "String"
        else -> "String"
    }
    return if (nullable) "$base?" else base
}

// Kotlin değişken adı için güvenli hale getir (tablo değişkeni vs.)
private fun safeIdent(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9_]"), "_")
    val prefixed = if (cleaned.firstOrNull()?.isDigit() == true) "_$cleaned" else cleaned
    return if (prefixed.isEmpty()) "_tbl" else prefixed
}

private fun titleCaseFirst(s: String): String =
    if (s.isEmpty()) s else s[0].uppercaseChar() + s.substring(1)

// Kolon adı → Kotlin ctor param adı (SQLDelight data class için)
// - snake_case ise aynen kalır (draft_year -> draft_year)
// - camel/Pascal ise ilk harf küçülür (Id -> id, UserName -> userName)
private fun toCtorParam(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9_]"), "_")
    return if (cleaned.contains("_")) cleaned else cleaned.replaceFirstChar { it.lowercase() }
}

private fun renderKotlin(pkg: String, tables: List<Tbl>, sqPkg: String): String = buildString {
    fun qt(s: String)  = "\"" + s.replace("\"","\\\"") + "\""
    fun qtn(s: String?) = s?.let { qt(it) } ?: "null"

    appendLine("package $pkg")
    appendLine()
    appendLine("import com.repzone.orm.meta.*")
    appendLine("import com.repzone.orm.registry.OrmRegistry")
    appendLine("import com.repzone.orm.runtime.DataRow")
    appendLine("import com.repzone.orm.runtime.RowMapper")
    appendLine()
    appendLine("// AUTO-GENERATED. DO NOT EDIT.")
    appendLine()

    // 1) Table meta’lar
    appendLine("object GeneratedTables {")
    tables.forEach { t ->
        val typeId = titleCaseFirst(t.name)
        val tableVar = safeIdent(t.name)
        appendLine("  val $tableVar = TableMeta(")
        appendLine("    tableName = ${qt(t.name)},")
        appendLine("    typeId = ${qt(typeId)},")
        appendLine("    columns = listOf(")
        t.cols.forEachIndexed { i, c ->
            val comma = if (i == t.cols.lastIndex) "" else ","
            appendLine(
                "      ColumnMeta(" +
                        "name=${qt(c.name)}," +
                        "affinity=ColumnAffinity.${toAffinity(c.type)}," +
                        "nullable=${(!c.notNull)}," +
                        "defaultValueSql=${qtn(c.dflt)}," +
                        "primaryKeyOrder=${c.pkOrder}," +
                        "autoIncrement=${c.autoInc}" +
                        ")$comma"
            )
        }
        appendLine("    )")
        appendLine("  )")
    }
    appendLine("}")
    appendLine()

    // 2) Registry
    appendLine("object OrmRegistryImpl : OrmRegistry {")
    appendLine("  override val tables: List<TableMeta> = listOf(")
    tables.forEachIndexed { i, t ->
        val tableVar = safeIdent(t.name)
        val comma = if (i == tables.lastIndex) "" else ","
        appendLine("    GeneratedTables.$tableVar$comma")
    }
    appendLine("  )")
    appendLine("}")
    appendLine()

    // 3) Her tablo için RowMapper: doğrudan SQLDelight data class kurucusu ile
    tables.forEach { t ->
        val typeId = titleCaseFirst(t.name)
        val fqSqlDelightType = "$sqPkg.$typeId"

        appendLine("object ${typeId}RowMapper : RowMapper<$fqSqlDelightType> {")
        appendLine("  override fun map(row: DataRow): $fqSqlDelightType = $fqSqlDelightType(")
        appendLine(
            t.cols.joinToString(",\n      ") { c ->
                val kt = kotlinTypeFromAffinity(toAffinity(c.type), !c.notNull)
                val param = toCtorParam(c.name)   // ctor param adı
                val col   = qt(c.name)            // DataRow içindeki kolon adı
                val expr = if (c.notNull) {
                    "row.get<$kt>($col) ?: error(${qt("${c.name} is null")})"
                } else {
                    "row.get<$kt>($col)"
                }
                "      $param = $expr"
            }
        )
        appendLine("  )")
        appendLine("}")
        appendLine()
    }

    // 4) EntityGenerated: T -> typeId ve T -> mapper
    appendLine("object EntityGenerated {")
    appendLine("  inline fun <reified T> typeIdOf(): String = when (T::class) {")
    tables.forEach { t ->
        val typeId = titleCaseFirst(t.name)
        val fq  = "$sqPkg.$typeId"
        appendLine("    $fq::class -> ${qt(typeId)}")
    }
    appendLine("    else -> error(\"Unsupported entity type: \${T::class}\")")
    appendLine("  }")
    appendLine()
    appendLine("  @Suppress(\"UNCHECKED_CAST\")")
    appendLine("  inline fun <reified T> mapperOf(): RowMapper<T> = when (T::class) {")
    tables.forEach { t ->
        val typeId = titleCaseFirst(t.name)
        val fq  = "$sqPkg.$typeId"
        appendLine("    $fq::class -> ${typeId}RowMapper as RowMapper<T>")
    }
    appendLine("    else -> error(\"No mapper for entity type: \${T::class}\")")
    appendLine("  }")
    appendLine("}")
}
