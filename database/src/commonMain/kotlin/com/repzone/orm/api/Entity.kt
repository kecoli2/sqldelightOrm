package com.repzone.orm.api

import app.cash.sqldelight.db.SqlDriver
import com.repzone.database.orm.generated.EntityGenerated
import com.repzone.database.orm.generated.OrmRegistryImpl
import com.repzone.orm.dsl.Expr
import com.repzone.orm.dsl.Limit
import com.repzone.orm.dsl.OrderSpec
import com.repzone.orm.runtime.DataRow
import com.repzone.orm.runtime.RowMapper
import com.repzone.orm.runtime.buildSelect
import com.repzone.orm.runtime.executeSelect


object Entity {
    inline fun <reified T> select(
        driver: SqlDriver,
        criteria: Expr? = null,
        order: List<OrderSpec> = emptyList(),
        limit: Limit? = null
    ): List<T> {
        val typeId = EntityGenerated.typeIdOf<T>()           // T -> typeId
        val table = OrmRegistryImpl.byTypeId(typeId)
            ?: error("Table meta not found for typeId=$typeId (T=${T::class})")

        val stmt = buildSelect(table, criteria, order, limit)
        val rows: List<DataRow> = executeSelect(driver, table, stmt)

        val mapper: RowMapper<T> = EntityGenerated.mapperOf<T>() // T -> RowMapper<T>
        return rows.map { mapper.map(it) }
    }
}
