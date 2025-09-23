package com.repzone.orm.runtime

import com.repzone.orm.dsl.Expr
import com.repzone.orm.dsl.Limit
import com.repzone.orm.dsl.OrderSpec
import com.repzone.orm.meta.ColumnAffinity
import com.repzone.orm.meta.TableMeta

private fun quoteIdent(id: String) = "\"${id.replace("\"", "\"\"")}\""

private data class BuiltWhere(val sql: String, val args: List<Any?>)

private fun buildWhere(expr: Expr): BuiltWhere {
    return when (expr) {
        is Expr.Eq -> BuiltWhere("${quoteIdent(expr.col)} = ?", listOf(expr.value))
        is Expr.Ne -> BuiltWhere("${quoteIdent(expr.col)} <> ?", listOf(expr.value))
        is Expr.Gt -> BuiltWhere("${quoteIdent(expr.col)} > ?", listOf(expr.value))
        is Expr.Ge -> BuiltWhere("${quoteIdent(expr.col)} >= ?", listOf(expr.value))
        is Expr.Lt -> BuiltWhere("${quoteIdent(expr.col)} < ?", listOf(expr.value))
        is Expr.Le -> BuiltWhere("${quoteIdent(expr.col)} <= ?", listOf(expr.value))
        is Expr.Like -> BuiltWhere("${quoteIdent(expr.col)} LIKE ?", listOf(expr.pattern))
        is Expr.IsNull -> BuiltWhere("${quoteIdent(expr.col)} IS NULL", emptyList())
        is Expr.IsNotNull -> BuiltWhere("${quoteIdent(expr.col)} IS NOT NULL", emptyList())
        is Expr.InList -> {
            val qs = List(expr.values.size) { "?" }.joinToString(",")
            BuiltWhere("${quoteIdent(expr.col)} IN ($qs)", expr.values)
        }
        is Expr.And -> {
            val parts = expr.items.map { buildWhere(it) }
            BuiltWhere(parts.joinToString(" AND ") { "(${it.sql})" }, parts.flatMap { it.args })
        }
        is Expr.Or -> {
            val parts = expr.items.map { buildWhere(it) }
            BuiltWhere(parts.joinToString(" OR ") { "(${it.sql})" }, parts.flatMap { it.args })
        }
    }
}

fun buildSelect(table: TableMeta, criteria: Expr? = null, order: List<OrderSpec> = emptyList(), limit: Limit? = null): SqlStatement {
    val sb = StringBuilder()
    val args = mutableListOf<Any?>()

    val projection = table.columns.joinToString(",") { quoteIdent(it.name) }
    sb.append("SELECT ").append(projection)
        .append(" FROM ").append(quoteIdent(table.tableName))

    if (criteria != null) {
        val w = buildWhere(criteria)
        sb.append(" WHERE ").append(w.sql)
        args.addAll(w.args)
    }

    if (order.isNotEmpty()) {
        sb.append(" ORDER BY ")
        sb.append(order.joinToString(",") { "${quoteIdent(it.col)} ${if (it.asc) "ASC" else "DESC"}" })
    }

    if (limit != null) {
        sb.append(" LIMIT ?")
        args += limit.limit
        if (limit.offset > 0) {
            sb.append(" OFFSET ?")
            args += limit.offset
        }
    }

    return SqlStatement(sb.toString(), args.toList())
}

internal fun affinityReadOrder(affinity: ColumnAffinity): Int = when (affinity) {
    ColumnAffinity.INTEGER -> 0
    ColumnAffinity.REAL    -> 1
    ColumnAffinity.TEXT    -> 2
    ColumnAffinity.BLOB    -> 3
    ColumnAffinity.NUMERIC -> 4
    ColumnAffinity.UNKNOWN -> 5
}