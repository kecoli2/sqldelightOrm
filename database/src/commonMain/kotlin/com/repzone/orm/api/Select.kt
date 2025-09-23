package com.repzone.orm.api

import app.cash.sqldelight.db.SqlDriver
import com.repzone.orm.dsl.Expr
import com.repzone.orm.dsl.Limit
import com.repzone.orm.dsl.OrderSpec
import com.repzone.orm.meta.TableMeta
import com.repzone.orm.runtime.DataRow
import com.repzone.orm.runtime.buildSelect
import com.repzone.orm.runtime.executeSelect

object Orm {
    fun select(
        driver: SqlDriver,
        table: TableMeta,
        criteria: Expr? = null,
        order: List<OrderSpec> = emptyList(),
        limit: Limit? = null
    ): List<DataRow> {
        val stmt = buildSelect(table, criteria, order, limit)
        return executeSelect(driver, table, stmt)
    }
}