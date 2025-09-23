package com.repzone.orm
import com.repzone.database.AppDatabase

object Entity {

    inline fun <reified T> select(
        database: AppDatabase,
        options: SelectOptions = SelectOptions()
    ): List<T> {
        val metadata = EntityRegistry.getMetadata<T>()
        return executeSelect(database, metadata, options)
    }

    inline fun <reified T> select(
        database: AppDatabase,
        where: List<WhereCondition> = emptyList(),
        orderBy: List<OrderCondition> = emptyList(),
        limit: Int? = null
    ): List<T> {
        return select<T>(database, SelectOptions(where, orderBy, limit = limit))
    }

    inline fun <reified T> select(
        database: AppDatabase,
        builder: SelectBuilder.() -> Unit
    ): List<T> {
        val selectBuilder = SelectBuilder()
        selectBuilder.builder()
        return select<T>(database, selectBuilder.build())
    }
}