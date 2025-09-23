package com.repzone.orm.gen.processor

data class TableEntity(
    val className: String,
    val tableName: String,
    val columns: List<TableColumn>
)

data class TableColumn(
    val name: String,
    val propertyName: String,
    val sqlType: String,
    val kotlinType: String,
    val nullable: Boolean,
    val isPrimaryKey: Boolean = false,
    val defaultValue: String? = null
)