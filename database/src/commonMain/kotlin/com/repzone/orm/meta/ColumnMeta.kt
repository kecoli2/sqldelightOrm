package com.repzone.orm.meta

data class ColumnMeta(
    val name: String,
    val affinity: ColumnAffinity,
    val nullable: Boolean,
    val defaultValueSql: String?, // ham SQL default ifadesi
    val primaryKeyOrder: Int,     // 0 = PK değil, >0 = PK sırası
    val autoIncrement: Boolean
)