package com.repzone.orm.gen.processor

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class DatabaseSchemaProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val dbPath = options["database.path"] ?: run {
            logger.error("database.path option is required")
            return emptyList()
        }

        val packageName = options["database.packageName"] ?: "com.repzone.orm.database"

        val dbFile = File(dbPath)
        if (!dbFile.exists()) {
            logger.error("Database file not found: $dbPath")
            return emptyList()
        }

        try {
            logger.info("Reading database schema from: $dbPath")
            val entities = readSchemaFromDatabase(dbFile)
            logger.info("Found ${entities.size} tables: ${entities.map { it.className }}")

            entities.forEach { entity ->
                generateMetadataClass(entity, packageName)
                logger.info("Generated metadata for: ${entity.className}")
            }

            if (entities.isNotEmpty()) {
                generateRegistrationFile(entities, packageName)
                logger.info("Generated registration file")
            }

        } catch (e: Exception) {
            logger.error("Error reading database schema: ${e.message}")
            logger.exception(e)
        }

        return emptyList()
    }

    private fun readSchemaFromDatabase(dbFile: File): List<TableEntity> {
        val entities = mutableListOf<TableEntity>()
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

        try {
            val tablesQuery = """
                SELECT name FROM sqlite_master 
                WHERE type='table' 
                AND name NOT LIKE 'sqlite_%'
                AND name NOT LIKE 'android_metadata'
                ORDER BY name
            """.trimIndent()

            val tablesStmt = connection.prepareStatement(tablesQuery)
            val tablesResult = tablesStmt.executeQuery()

            while (tablesResult.next()) {
                val tableName = tablesResult.getString("name")
                logger.info("Processing table: $tableName")

                val columns = getTableColumns(connection, tableName)

                if (columns.isNotEmpty()) {
                    val className = tableNameToClassName(tableName)
                    entities.add(TableEntity(
                        className = className,
                        tableName = tableName,
                        columns = columns
                    ))
                    logger.info("Table $tableName -> Class $className with ${columns.size} columns")
                }
            }

        } finally {
            connection.close()
        }

        return entities
    }

    private fun getTableColumns(connection: Connection, tableName: String): List<TableColumn> {
        val columns = mutableListOf<TableColumn>()
        val columnsQuery = "PRAGMA table_info($tableName)"
        val stmt = connection.prepareStatement(columnsQuery)
        val result = stmt.executeQuery()

        while (result.next()) {
            val columnName = result.getString("name")
            val columnType = result.getString("type")
            val notNull = result.getInt("notnull") == 1
            val defaultValue = result.getString("dflt_value")
            val isPrimaryKey = result.getInt("pk") > 0

            val kotlinType = sqliteTypeToKotlinType(columnType)
            val propertyName = columnNameToPropertyName(columnName)

            columns.add(TableColumn(
                name = columnName,
                propertyName = propertyName,
                sqlType = columnType,
                kotlinType = kotlinType,
                nullable = !notNull,
                isPrimaryKey = isPrimaryKey,
                defaultValue = defaultValue
            ))

            logger.info("  Column: $columnName ($columnType) -> $propertyName ($kotlinType)")
        }

        return columns
    }

    private fun sqliteTypeToKotlinType(sqlType: String): String {
        return when (sqlType.uppercase()) {
            "INTEGER" -> "Long"
            "TEXT" -> "String"
            "REAL" -> "Double"
            "BLOB" -> "ByteArray"
            "BOOLEAN" -> "Boolean"
            else -> when {
                sqlType.contains("INT", ignoreCase = true) -> "Long"
                sqlType.contains("CHAR", ignoreCase = true) -> "String"
                sqlType.contains("TEXT", ignoreCase = true) -> "String"
                sqlType.contains("REAL", ignoreCase = true) -> "Double"
                sqlType.contains("FLOAT", ignoreCase = true) -> "Double"
                sqlType.contains("DOUBLE", ignoreCase = true) -> "Double"
                else -> "String"
            }
        }
    }

    private fun tableNameToClassName(tableName: String): String {
        return if (tableName.contains("_")) {
            tableName.split("_").joinToString("") {
                it.lowercase().replaceFirstChar { char -> char.uppercaseChar() }
            }
        } else {
            tableName.replaceFirstChar { it.uppercaseChar() }
        }
    }

    private fun columnNameToPropertyName(columnName: String): String {
        return if (columnName.contains("_")) {
            val parts = columnName.split("_")
            parts.first().lowercase() + parts.drop(1).joinToString("") {
                it.lowercase().replaceFirstChar { char -> char.uppercaseChar() }
            }
        } else {
            columnName.lowercase()
        }
    }

    private fun generateMetadataClass(entity: TableEntity, packageName: String) {
        val content = buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.repzone.orm.database.BaseEntityMetadata")
            appendLine()
            appendLine("class ${entity.className}Metadata : BaseEntityMetadata<${entity.className}>() {")
            appendLine("    override val tableName = \"${entity.tableName}\"")

            // fields list
            appendLine("    override val fields = listOf(")
            entity.columns.forEach { column ->
                appendLine("        \"${column.name}\",")
            }
            appendLine("    )")

            // fieldMappings map
            appendLine("    override val fieldMappings = mapOf(")
            entity.columns.forEach { column ->
                appendLine("        \"${column.propertyName}\" to \"${column.name}\",")
            }
            appendLine("    )")
            appendLine()

            // createInstance method
            appendLine("    override fun createInstance(values: Map<String, Any?>): ${entity.className} {")
            appendLine("        return ${entity.className}(")
            entity.columns.forEach { column ->
                val castType = if (column.nullable) "${column.kotlinType}?" else column.kotlinType
                appendLine("            ${column.propertyName} = convertDbValueToKotlin(values[\"${column.name}\"], \"${column.kotlinType}\") as $castType,")
            }
            appendLine("        )")
            appendLine("    }")
            appendLine()

            // extractValues method
            appendLine("    override fun extractValues(instance: ${entity.className}): Map<String, Any?> {")
            appendLine("        return mapOf(")
            entity.columns.forEach { column ->
                appendLine("            \"${column.name}\" to convertKotlinValueToDb(instance.${column.propertyName}),")
            }
            appendLine("        )")
            appendLine("    }")
            appendLine("}")
        }

        writeFileManually(content, packageName, "${entity.className}Metadata")
    }

    private fun generateRegistrationFile(entities: List<TableEntity>, packageName: String) {
        val content = buildString {
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.repzone.orm.database.EntityRegistry")
            appendLine()
            appendLine("object Generated_EntityRegistration {")
            appendLine("    fun registerAll() {")
            entities.forEach { entity ->
                appendLine("        EntityRegistry.registerInternal(\"${entity.className}\", ${entity.className}Metadata())")
            }
            appendLine("    }")
            appendLine("}")
        }

        writeFileManually(content, packageName, "Generated_EntityRegistration")
    }

    private fun writeFileManually(content: String, packageName: String, fileName: String) {
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = packageName,
            fileName = fileName
        )

        file.use { outputStream ->
            outputStream.write(content.toByteArray())
        }
    }
}