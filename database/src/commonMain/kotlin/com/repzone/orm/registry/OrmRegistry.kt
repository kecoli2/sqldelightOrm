package com.repzone.orm.registry

import com.repzone.orm.meta.TableMeta

interface OrmRegistry {
    val tables: List<TableMeta>
    fun byTypeId(id: String): TableMeta? = tables.firstOrNull { it.typeId == id }
}