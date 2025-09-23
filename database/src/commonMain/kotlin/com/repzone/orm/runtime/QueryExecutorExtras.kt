package com.repzone.orm.runtime

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

private fun SqlPreparedStatement.bindAny(index: Int, v: Any?) {
    when (v) {
        null         -> bindAny(index, null)
        is Long      -> bindLong(index, v)
        is Int       -> bindLong(index, v.toLong())
        is Short     -> bindLong(index, v.toLong())
        is Byte      -> bindLong(index, v.toLong())
        is Boolean   -> bindLong(index, if (v) 1L else 0L)
        is Double    -> bindDouble(index, v)
        is Float     -> bindDouble(index, v.toDouble())
        is String    -> bindString(index, v)
        is ByteArray -> bindBytes(index, v)
        else         -> bindString(index, v.toString())
    }
}

private fun quoteIdent(id: String) = "\"${id.replace("\"","\"\"")}\""

fun executeCount(
    driver: SqlDriver,
    tableName: String,
    where: WhereClause
): Long {
    val sql = buildString {
        append("SELECT COUNT(*) FROM ").append(quoteIdent(tableName))
        if (!where.sql.isNullOrBlank()) append(" WHERE ").append(where.sql)
    }
    val result: QueryResult<Long> = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val v = if (cursor.next().value) (cursor.getLong(0) ?: 0L) else 0L
            QueryResult.Value(v)
        },
        parameters = where.args.size,
        binders = { where.args.forEachIndexed { i, v -> bindAny(i, v) } } // 0-based
    )
    return result.value
}