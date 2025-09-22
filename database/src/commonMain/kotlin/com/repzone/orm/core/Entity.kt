// src/commonMain/kotlin/com/repzone/orm/core/Entity.kt
package com.repzone.orm.core

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.reflect.KClass

// ===== Public DSL =====
enum class Op { EQ, NE, GT, GE, LT, LE, LIKE, IN, IS_NULL, IS_NOT_NULL }
data class Criterion(val column: String, val op: Op, val value: Any? = null)
data class OrderBy(val column: String, val asc: Boolean = true)

fun eq(col: String, v: Any?) = Criterion(col, Op.EQ, v)
fun ne(col: String, v: Any?) = Criterion(col, Op.NE, v)
fun gt(col: String, v: Any?) = Criterion(col, Op.GT, v)
fun ge(col: String, v: Any?) = Criterion(col, Op.GE, v)
fun lt(col: String, v: Any?) = Criterion(col, Op.LT, v)
fun le(col: String, v: Any?) = Criterion(col, Op.LE, v)
fun like(col: String, v: String) = Criterion(col, Op.LIKE, v)
fun inList(col: String, values: List<Any?>) = Criterion(col, Op.IN, values)
fun isNull(col: String) = Criterion(col, Op.IS_NULL)
fun isNotNull(col: String) = Criterion(col, Op.IS_NOT_NULL)
fun asc(col: String) = OrderBy(col, true)
fun desc(col: String) = OrderBy(col, false)

// ===== Runtime Registry (generated dosya doldurur) =====
object OrmRegistry {
    @Suppress("unused")
    private val _forceInit = com.repzone.orm.generated.OrmRegistry_Generated
    internal val _tableNames = mutableMapOf<KClass<*>, String>()
    internal val _columns    = mutableMapOf<KClass<*>, List<String>>()
    internal val _mappers    = mutableMapOf<KClass<*>, (SqlCursor) -> Any>()

    fun <T: Any> tableNameOf(klass: KClass<T>): String =
        _tableNames[klass] ?: error("No table registered for ${klass.simpleName}")

    fun <T: Any> columnsOf(klass: KClass<T>): List<String> =
        _columns[klass] ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    fun <T: Any> mapperOf(klass: KClass<T>): (SqlCursor) -> T =
        (_mappers[klass] as? (SqlCursor) -> T)
            ?: error("No mapper registered for ${klass.simpleName}")
}

// ===== Core =====
object Entity {

    inline fun <reified T: Any> select(
        driver: SqlDriver,
        where: List<Criterion> = emptyList(),
        orderBy: List<OrderBy> = emptyList(),
        limit: Long? = null,
        offset: Long? = null
    ): List<T> {
        val table = OrmRegistry.tableNameOf(T::class)
        val cols = OrmRegistry.columnsOf(T::class)
        val colSet = cols.map { it.lowercase() }.toSet()

        val args = mutableListOf<Any?>()

        // WHERE
        val whereSql = buildString {
            if (where.isNotEmpty()) {
                append(" WHERE ")
                where.forEachIndexed { i, c ->
                    require(colSet.contains(c.column.lowercase())) {
                        "Invalid column ${c.column} for table $table"
                    }
                    if (i > 0) append(" AND ")
                    append(c.column).append(' ')
                    when (c.op) {
                        Op.EQ -> { append("= ?"); args.add(c.value) }
                        Op.NE -> { append("<> ?"); args.add(c.value) }
                        Op.GT -> { append("> ?"); args.add(c.value) }
                        Op.GE -> { append(">= ?"); args.add(c.value) }
                        Op.LT -> { append("< ?"); args.add(c.value) }
                        Op.LE -> { append("<= ?"); args.add(c.value) }
                        Op.LIKE -> { append("LIKE ?"); args.add(c.value) }
                        Op.IN -> {
                            val list = (c.value as? List<*>) ?: emptyList<Any?>()
                            require(list.isNotEmpty()) { "IN needs non-empty list" }
                            append("IN (")
                            append(List(list.size) { "?" }.joinToString(", "))
                            append(")")
                            args.addAll(list)
                        }
                        Op.IS_NULL -> append("IS NULL")
                        Op.IS_NOT_NULL -> append("IS NOT NULL")
                    }
                }
            }
        }

        // ORDER BY
        val orderSql = orderBy
            .filter { colSet.contains(it.column.lowercase()) }
            .joinToString(", ") { "${it.column} ${if (it.asc) "ASC" else "DESC"}" }
            .let { if (it.isNotEmpty()) " ORDER BY $it" else "" }

        // LIMIT/OFFSET
        val limitSql = if (limit != null) " LIMIT ?" else ""
        val offsetSql = if (limit != null && offset != null) " OFFSET ?" else ""
        if (limit != null) args.add(limit)
        if (limit != null && offset != null) args.add(offset)

        val sql = "SELECT * FROM $table$whereSql$orderSql$limitSql$offsetSql"

        // --- executeQuery: mapper (SqlCursor)-> QueryResult<List<T>> ---
        val rowsQR: QueryResult<List<T>> = driver.executeQuery(
            /* identifier = */ null,
            /* sql        = */ sql,
            /* mapper     = */ { cursor: SqlCursor ->
                val mapper = OrmRegistry.mapperOf(T::class)
                val out = ArrayList<T>()
                while (cursor.next().value) {
                    out.add(mapper(cursor))
                }
                QueryResult.Value(out) // <-- ÖNEMLİ: QueryResult<List<T>> döndür
            },
            /* parameters = */ args.size
        ) {
            // binder
            args.forEachIndexed { i, v ->
                when (v) {
                    null -> bindString(i, null)          // null'u böyle bağla
                    is Long -> bindLong(i, v)
                    is Int -> bindLong(i, v.toLong())
                    is Boolean -> bindLong(i, if (v) 1L else 0L)
                    is Double -> bindDouble(i, v)
                    is Float -> bindDouble(i, v.toDouble())
                    is String -> bindString(i, v)
                    is ByteArray -> bindBytes(i, v)
                    else -> bindString(i, v.toString())
                }
            }
        }

        return rowsQR.value
    }
}
