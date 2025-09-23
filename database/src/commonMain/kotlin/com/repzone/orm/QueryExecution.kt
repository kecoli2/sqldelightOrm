package com.repzone.orm

import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.repzone.database.AppDatabase

fun <T> executeSelect(
    database: AppDatabase,
    metadata: EntityMetadata<T>,
    options: SelectOptions
): List<T> {
    val sqlBuilder = SQLQueryBuilder()
    val (query, parameters) = sqlBuilder.buildSelectQuery(metadata.tableName, metadata.fields, options)

    val cursor = database.driver.executeQuery(
        identifier = null,
        sql = query,
        parameters = parameters.size
    ) { statementIndex ->
        parameters.forEachIndexed { index, param ->
            bindParameter(database.driver, statementIndex, index, param)
        }
    }

    val results = mutableListOf<T>()
    while (cursor.next()) {
        val values = mutableMapOf<String, Any?>()
        metadata.fields.forEachIndexed { index, field ->
            values[field] = when {
                cursor.getString(index) != null -> cursor.getString(index)
                cursor.getLong(index) != null -> cursor.getLong(index)
                cursor.getDouble(index) != null -> cursor.getDouble(index)
                else -> null
            }
        }
        results.add(metadata.createInstance(values))
    }
    cursor.close()

    return results
}

private fun bindParameter(driver: SqlDriver, statementIndex: Int, parameterIndex: Int, value: Any?) {
    when (value) {
        is String? -> driver.bindString(statementIndex, parameterIndex, value)
        is Long -> driver.bindLong(statementIndex, parameterIndex, value)
        is Int -> driver.bindLong(statementIndex, parameterIndex, value.toLong())
        is Boolean -> driver.bindLong(statementIndex, parameterIndex, if (value) 1L else 0L)
        is Double -> driver.bindDouble(statementIndex, parameterIndex, value)
        is ByteArray -> driver.bindBytes(statementIndex, parameterIndex, value)
        null -> driver.bindString(statementIndex, parameterIndex, null)
        else -> driver.bindString(statementIndex, parameterIndex, value.toString())
    }
}