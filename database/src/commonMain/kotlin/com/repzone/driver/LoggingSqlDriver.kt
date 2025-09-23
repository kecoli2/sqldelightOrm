package com.repzone.driver

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

class LoggingSqlDriver(private val delegate: SqlDriver) : SqlDriver by delegate {

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<Long> {
        logQuery("EXECUTE", sql, parameters)
        return delegate.execute(identifier, sql, parameters, binders)
    }

    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
        logQuery("QUERY", sql, parameters)
        return delegate.executeQuery(identifier, sql, mapper, parameters, binders)
    }

    private fun logQuery(type: String, sql: String, parameterCount: Int) {
        platformLog("SQLDelight-$type", "SQL: $sql ${if (parameterCount > 0) "| Params: $parameterCount" else ""}")
    }
}

expect fun platformLog(tag: String, message: String)