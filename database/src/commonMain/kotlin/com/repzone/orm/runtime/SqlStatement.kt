package com.repzone.orm.runtime

data class SqlStatement(
    val sql: String,
    val args: List<Any?>
)