package com.repzone.orm.api

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long? = null,   // includeTotal=true ise dolu gelir
    val hasNext: Boolean
)