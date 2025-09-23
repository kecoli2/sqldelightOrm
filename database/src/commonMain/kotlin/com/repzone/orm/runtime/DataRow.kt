package com.repzone.orm.runtime

class DataRow internal constructor(private val map: Map<String, Any?>) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(name: String): T? = map[name] as T?
    fun names(): Set<String> = map.keys
    override fun toString(): String = "DataRow(${map.entries.joinToString()})"
}