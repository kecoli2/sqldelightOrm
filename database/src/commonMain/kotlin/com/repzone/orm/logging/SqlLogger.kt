package com.repzone.orm.logging

interface SqlLogger {
    fun log(sql: String, args: List<Any?>)
}

internal fun formatSql(sql: String, args: List<Any?>): String {
    // '?' yer tutucularını olduğu gibi bırakıp args'ları ayrıca loglayalım (güvenli)
    return "SQL: $sql | args=$args"
}