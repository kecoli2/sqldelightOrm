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
import com.repzone.orm.runtime.buildWhereClause
import com.repzone.orm.runtime.executeCount
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

    /**
     * Offset-based sayfalama.
     * @param includeTotal true ise toplam kayıt sayısını ayrı bir COUNT(*) sorgusuyla getirir.
     */
    inline fun <reified T> page(
        driver: SqlDriver,
        criteria: Expr? = null,
        order: List<OrderSpec> = emptyList(),
        page: Int,
        size: Int,
        includeTotal: Boolean = false
    ): Page<T> {
        val safePage = if (page < 1) 1 else page
        val safeSize = if (size < 1) 1 else size
        val lim = Limit.page(safePage, safeSize)

        val typeId = EntityGenerated.typeIdOf<T>()
        val table  = OrmRegistryImpl.byTypeId(typeId)
            ?: error("Table meta not found for typeId=$typeId (T=${T::class})")

        // SELECT verileri
        val stmt = buildSelect(table, criteria, order, lim)
        val rows: List<DataRow> = executeSelect(driver, table, stmt)
        val mapper = EntityGenerated.mapperOf<T>()
        val items = rows.map { mapper.map(it) }

        // COUNT (opsiyonel)
        val total: Long? = if (includeTotal) {
            val where = buildWhereClause(criteria)
            executeCount(driver, table.tableName, where)
        } else null

        val hasNext = when {
            total != null -> lim.offset + items.size < total
            else -> items.size == safeSize
        }

        return Page(
            items = items,
            page = safePage,
            size = safeSize,
            total = total,
            hasNext = hasNext
        )
    }
}
