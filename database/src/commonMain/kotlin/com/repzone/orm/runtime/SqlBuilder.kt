package com.repzone.orm.runtime

import com.repzone.orm.dsl.*
import com.repzone.orm.meta.ColumnAffinity
import com.repzone.orm.meta.TableMeta

private fun quoteIdent(id: String) = "\"${id.replace("\"", "\"\"")}\""

data class WhereClause(val sql: String?, val args: List<Any?>)

private data class BuiltWhere(val sql: String, val args: List<Any?>)

private fun build(expr: Expr): BuiltWhere = when (expr) {
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
        require(expr.values.isNotEmpty()) { "IN list empty" }
        val qs = List(expr.values.size) { "?" }.joinToString(",")
        BuiltWhere("${quoteIdent(expr.col)} IN ($qs)", expr.values)
    }
    is Expr.Between -> BuiltWhere("${quoteIdent(expr.col)} BETWEEN ? AND ?", listOf(expr.start, expr.end))
    is Expr.And -> {
        val parts = expr.items.map { build(it) }
        BuiltWhere(parts.joinToString(" AND ") { "(${it.sql})" }, parts.flatMap { it.args })
    }
    is Expr.Or -> {
        val parts = expr.items.map { build(it) }
        BuiltWhere(parts.joinToString(" OR ") { "(${it.sql})" }, parts.flatMap { it.args })
    }
    is Expr.Not -> {
        val inner = build(expr.item)
        BuiltWhere("NOT (${inner.sql})", inner.args)
    }
    is Expr.Group -> {
        val inner = build(expr.item)
        BuiltWhere("(${inner.sql})", inner.args)
    }
}

/** Count/exists için WHERE parçasını tek başına üretir. */
fun buildWhereClause(criteria: Expr?): WhereClause {
    if (criteria == null) return WhereClause(null, emptyList())
    val w = build(criteria)
    return WhereClause(w.sql, w.args)
}

/** SELECT builder (projection: tüm sütunlar) */
fun buildSelect(
    table: TableMeta,
    criteria: Expr? = null,
    order: List<OrderSpec> = emptyList(),
    limit: Limit? = null
): SqlStatement {
    val sb = StringBuilder()
    val params = mutableListOf<Any?>()

    val projection = table.columns.joinToString(",") { quoteIdent(it.name) }
    sb.append("SELECT ").append(projection)
        .append(" FROM ").append(quoteIdent(table.tableName))

    val where = buildWhereClause(criteria)
    if (!where.sql.isNullOrBlank()) {
        sb.append(" WHERE ").append(where.sql)
        params.addAll(where.args)
    }

    if (order.isNotEmpty()) {
        sb.append(" ORDER BY ")
        sb.append(order.joinToString(",") {
            "${quoteIdent(it.col)} ${if (it.asc) "ASC" else "DESC"}"
        })
    }

    if (limit != null) {
        sb.append(" LIMIT ?")
        params.add(limit.limit)
        if (limit.offset > 0) {
            sb.append(" OFFSET ?")
            params.add(limit.offset)
        }
    }

    return SqlStatement(sb.toString(), params.toList())
}

internal fun affinityReadOrder(affinity: ColumnAffinity): Int = when (affinity) {
    ColumnAffinity.INTEGER -> 0
    ColumnAffinity.REAL    -> 1
    ColumnAffinity.TEXT    -> 2
    ColumnAffinity.BLOB    -> 3
    ColumnAffinity.NUMERIC -> 4
    ColumnAffinity.UNKNOWN -> 5
}
