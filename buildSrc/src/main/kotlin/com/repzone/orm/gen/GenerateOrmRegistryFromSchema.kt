// buildSrc (veya ilgili modül)/src/main/kotlin/com/repzone/orm/gen/GenerateOrmRegistryFromSchema.kt
package com.repzone.orm.gen

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * .db şemasından OrmRegistry_Generated.kt üretir.
 *
 * GİRİŞ:
 *  - schemaDb: migration verification'dan gelen en güncel .db (CommonMainAppDatabase/NN.db)
 *  - generatedPackage: "com.repzone.orm.generated"
 *  - rowTypesPackage:  "com.repzone.db"  (SQLDelight data class'larının paketi)
 *  - outputKotlinFile: build/.../OrmRegistry_Generated.kt
 *  - typeHints: opsiyonel; "Table.column=KotlinType" (Int,Long,Double,String,Bytes,Bool) ipuçları
 *  - ctorNameHints: opsiyonel; "Table=ClassSimpleName" (tablo adı ile class adı farklıysa)
 *  - customConverters: opsiyonel; "Table.column=expr(%s)" — expr içine okunan değeri koyar (ör: Instant.parse(%s))
 */
abstract class GenerateOrmRegistryFromSchema : DefaultTask() {

    @get:InputFile
    abstract val schemaDb: RegularFileProperty

    @get:Input
    abstract val generatedPackage: Property<String>

    @get:Input
    abstract val rowTypesPackage: Property<String>

    @get:OutputFile
    abstract val outputKotlinFile: RegularFileProperty

    // Örn: User.age=Int , User.isActive=Bool
    @get:Input
    @get:Optional
    abstract val typeHints: MapProperty<String, String>

    // Örn: User=UserDto (tablo adı → sınıf adı)
    @get:Input
    @get:Optional
    abstract val ctorNameHints: MapProperty<String, String>

    // Örn: User.createdAt=kotlinx.datetime.Instant.parse(%s)
    @get:Input
    @get:Optional
    abstract val customConverters: MapProperty<String, String>

    @TaskAction
    fun run() {
        val db = schemaDb.get().asFile
        require(db.exists()) { "schemaDb not found: $db" }

        Class.forName("org.sqlite.JDBC") // xerial
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { conn ->
            val tables = listTables(conn)
            val packageName = generatedPackage.get()
            val rowsPkg = rowTypesPackage.get()

            val sb = StringBuilder()
            sb.appendLine("package $packageName")
            sb.appendLine()
            sb.appendLine("import app.cash.sqldelight.db.SqlCursor")
            sb.appendLine("import com.repzone.orm.runtime.OrmRegistry")
            sb.appendLine("import kotlin.reflect.KClass")
            sb.appendLine()
            // imports for row types (we import simple per table)
            tables.forEach { t ->
                val className = ctorNameHints.get().getOrElse(t) { t }
                sb.appendLine("import $rowsPkg.$className")
            }
            sb.appendLine()
            sb.appendLine(helperFns())
            sb.appendLine("@Suppress(\"UNCHECKED_CAST\")")
            sb.appendLine("object OrmRegistry_Generated {")
            sb.appendLine("  init {")

            for (t in tables) {
                val columns = tableColumns(conn, t)
                val className = ctorNameHints.get().getOrElse(t) { t }
                sb.appendLine()
                sb.appendLine("    // ----- $t -----")
                sb.appendLine("    register(")
                sb.appendLine("        kclass = $className::class,")
                sb.appendLine("        table = \"$t\",")
                sb.appendLine("        columns = listOf(${columns.joinToString(",") { "\"$it\"" }})")
                sb.appendLine("    ) { c ->")
                // read columns by affinity/type
                val reads = columns.mapIndexed { idx, col ->
                    val key = "$t.$col"
                    val hint = typeHints.get().getOrElse(key) { null }?.uppercase()
                    val call = when (hint) {
                        "INT"    -> "readLong(c,$idx)?.toInt()"
                        "LONG"   -> "readLong(c,$idx)"
                        "DOUBLE" -> "readDouble(c,$idx)"
                        "STRING" -> "readString(c,$idx)"
                        "BYTES"  -> "readBytes(c,$idx)"
                        "BOOL","BOOLEAN" -> "readBool(c,$idx)"
                        null -> "readAuto(c,$idx)" // otomatik: INTEGER→Long?, REAL→Double?...
                        else -> "readAuto(c,$idx)"
                    }
                    val conv = customConverters.get().getOrElse(key) { null }
                    if (conv != null) conv.replace("%s", call) else call
                }
                sb.appendLine("        $className(")
                sb.appendLine("            " + reads.joinToString(", ") + ""
                )
                sb.appendLine("        )")
                sb.appendLine("    }")
            }

            sb.appendLine("  }") // init
            sb.appendLine()
            sb.appendLine(
                """
                private fun <T: Any> register(
                    kclass: KClass<T>,
                    table: String,
                    columns: List<String>,
                    mapper: (SqlCursor) -> T
                ) {
                    OrmRegistry._tableNames[kclass] = table
                    OrmRegistry._columns[kclass] = columns
                    OrmRegistry._mappers[kclass] = mapper as (SqlCursor) -> Any
                }
                """.trimIndent()
            )
            sb.appendLine("}")

            val out = outputKotlinFile.get().asFile
            out.parentFile.mkdirs()
            out.writeText(sb.toString())
            logger.lifecycle("Generated ${out.absolutePath} for ${tables.size} table(s).")
        }
    }

    private fun listTables(conn: Connection): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name;")
                .use { rs ->
                    buildList {
                        while (rs.next()) add(rs.getString(1))
                    }
                }
        }

    private fun tableColumns(conn: Connection, table: String): List<String> =
        conn.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table);").use { rs ->
                buildList {
                    while (rs.next()) add(rs.getString("name"))
                }
            }
        }

    private fun helperFns(): String = """
        // — helpers generated into the same file —
        private fun readLong(c: SqlCursor, idx: Int): Long? = c.getLong(idx)
        private fun readDouble(c: SqlCursor, idx: Int): Double? = c.getDouble(idx)
        private fun readString(c: SqlCursor, idx: Int): String? = c.getString(idx)
        private fun readBytes(c: SqlCursor, idx: Int): ByteArray? = c.getBytes(idx)
        private fun readBool(c: SqlCursor, idx: Int): Boolean? = c.getLong(idx)?.let { it != 0L }

        // INTEGER/REAL/TEXT/BLOB'a göre otomatik seçim
        private fun readAuto(c: SqlCursor, idx: Int): Any? {
            // SQLDelight Cursor'da null olmayan first-available stratejisi
            // INTEGER -> getLong, REAL -> getDouble, TEXT -> getString, BLOB -> getBytes
            return c.getLong(idx) ?: c.getDouble(idx) ?: c.getString(idx) ?: c.getBytes(idx)
        }
    """.trimIndent()
}
