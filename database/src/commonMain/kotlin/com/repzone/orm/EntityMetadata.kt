package com.repzone.orm

interface EntityMetadata<T> {
    val tableName: String
    val fields: List<String>
    val fieldMappings: Map<String, String>
    fun createInstance(values: Map<String, Any?>): T
    fun extractValues(instance: T): Map<String, Any?>
}