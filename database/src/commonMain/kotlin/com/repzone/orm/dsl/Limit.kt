package com.repzone.orm.dsl

data class Limit(val limit: Int, val offset: Int = 0) {
    companion object {
        fun page(page: Int, size: Int): Limit {
            val p = if (page < 1) 1 else page
            val s = if (size < 1) 1 else size
            return Limit(limit = s, offset = (p - 1) * s)
        }
    }
}