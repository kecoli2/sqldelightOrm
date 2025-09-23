package com.repzone.orm.runtime


fun interface RowMapper<T> {
    fun map(row: DataRow): T
}