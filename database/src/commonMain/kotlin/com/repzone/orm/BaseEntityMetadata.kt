package com.repzone.orm

abstract class BaseEntityMetadata<T> : EntityMetadata<T> {
    protected fun convertDbValueToKotlin(value: Any?, targetType: String): Any? {
        return when (targetType) {
            "Boolean" -> (value as? Long) == 1L
            "Int" -> (value as? Long)?.toInt()
            "String" -> value as? String
            "Long" -> value as? Long
            "Double" -> value as? Double
            "Float" -> (value as? Double)?.toFloat()
            "ByteArray" -> value as? ByteArray
            else -> value
        }
    }

    protected fun convertKotlinValueToDb(value: Any?): Any? {
        return when (value) {
            is Boolean -> if (value) 1L else 0L
            is Int -> value.toLong()
            is Float -> value.toDouble()
            else -> value
        }
    }
}