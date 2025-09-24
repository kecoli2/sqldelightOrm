package com.repzone.orm.logging

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * SQLDelight 2.x için şeffaf logger driver.
 * Tüm execute / executeQuery çağrılarını loglar (SQL + bind argümanları).
 */
class LoggingSqlDriver(
    private val delegate: SqlDriver,
    private val logger: SqlLogger = DefaultSqlLogger()
) : SqlDriver by delegate {

    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
        val loggingBinders: SqlPreparedStatement.() -> Unit = {
            val real = this
            val captured = mutableMapOf<Int, Any?>()

            val proxy = object : SqlPreparedStatement by real {
                override fun bindBoolean(index: Int, boolean: Boolean?) {
                    TODO("Not yet implemented")
                }

                override fun bindBytes(index: Int, bytes: ByteArray?) {
                    captured[index] = "[bytes ${bytes?.size}]"
                    real.bindBytes(index, bytes)
                }

                override fun bindDouble(index: Int, double: Double?) {
                    captured[index] = double
                    real.bindDouble(index, double)
                }

                override fun bindLong(index: Int, long: Long?) {
                    captured[index] = long
                    real.bindLong(index, long)
                }

                override fun bindString(index: Int, string: String?) {
                    captured[index] = string
                    real.bindString(index, string)
                }
            }

            binders?.invoke(proxy)

            // 0-based param indexi (SQLDelight 2.x)
            val args = (0 until parameters).map { captured[it] }
            logger.log(sql, args)
        }

        return delegate.executeQuery(identifier, sql, mapper, parameters, loggingBinders)
    }

    /*

    override fun execute(identifier: Int?, sql: String, parameters: Int, binders: SqlPreparedStatement.() -> Unit): QueryResult<Unit> {
        val loggingBinders: SqlPreparedStatement.() -> Unit = {
            val real = this
            val captured = mutableMapOf<Int, Any?>()

            val proxy = object : SqlPreparedStatement by real {
                override fun bindNull(index: Int) {
                    captured[index] = null
                    real.bindNull(index)
                }
                override fun bindLong(index: Int, value: Long) {
                    captured[index] = value
                    real.bindLong(index, value)
                }
                override fun bindDouble(index: Int, value: Double) {
                    captured[index] = value
                    real.bindDouble(index, value)
                }
                override fun bindString(index: Int, value: String) {
                    captured[index] = value
                    real.bindString(index, value)
                }
                override fun bindBytes(index: Int, value: ByteArray) {
                    captured[index] = "[bytes ${value.size}]"
                    real.bindBytes(index, value)
                }
            }

            binders(proxy)
            val args = (0 until parameters).map { captured[it] }
            logger.log(sql, args)
        }

        return delegate.execute(identifier, sql, parameters, loggingBinders)
    }*/
}
