package com.repzone.orm.meta

data class TableMeta(
    val tableName: String,
    val typeId: String,            // stabil kimlik (Users, Cities vs.)
    val columns: List<ColumnMeta>
)