package com.repzone.orm.logging

expect fun platformLog(tag: String, message: String)

class DefaultSqlLogger(private val tag: String = "ORM-SQL") : SqlLogger {
    override fun log(sql: String, args: List<Any?>) {
        platformLog(tag, formatSql(sql, args))
    }
}