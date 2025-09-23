package com.repzone.orm.dsl

sealed interface Expr {
    data class Eq(val col: String, val value: Any?) : Expr
    data class Ne(val col: String, val value: Any?) : Expr
    data class Gt(val col: String, val value: Any) : Expr
    data class Ge(val col: String, val value: Any) : Expr
    data class Lt(val col: String, val value: Any) : Expr
    data class Le(val col: String, val value: Any) : Expr
    data class Like(val col: String, val pattern: String) : Expr
    data class InList(val col: String, val values: List<Any?>) : Expr
    data class Between(val col: String, val start: Any, val end: Any) : Expr
    data class IsNull(val col: String) : Expr
    data class IsNotNull(val col: String) : Expr
    data class And(val items: List<Expr>) : Expr
    data class Or(val items: List<Expr>) : Expr
    data class Not(val item: Expr) : Expr
    /** DSL’de parantez zorlamak için */
    data class Group(val item: Expr) : Expr
}

// Vararg yardımcılar (explicit)
fun and(vararg e: Expr) = when {
    e.isEmpty() -> error("and() requires at least 1 Expr")
    e.size == 1 -> e[0]
    else -> Expr.And(e.toList())
}

fun or(vararg e: Expr) = when {
    e.isEmpty() -> error("or() requires at least 1 Expr")
    e.size == 1 -> e[0]
    else -> Expr.Or(e.toList())
}

// İnfiks operatör gibi akıcı kullanım
infix fun Expr.and(other: Expr): Expr =
    if (this is Expr.And) Expr.And(this.items + other) else Expr.And(listOf(this, other))

infix fun Expr.or(other: Expr): Expr =
    if (this is Expr.Or) Expr.Or(this.items + other) else Expr.Or(listOf(this, other))

/** !expr şeklinde NOT */
operator fun Expr.not(): Expr = Expr.Not(this)

/** DSL blokları */
fun group(block: () -> Expr): Expr = Expr.Group(block())
fun where(block: () -> Expr): Expr = block()

// Kolon adı üzerinden pratik kısayollar
infix fun String.eq(v: Any?) = Expr.Eq(this, v)
infix fun String.neq(v: Any?) = Expr.Ne(this, v)
infix fun String.gt(v: Any) = Expr.Gt(this, v)
infix fun String.ge(v: Any) = Expr.Ge(this, v)
infix fun String.lt(v: Any) = Expr.Lt(this, v)
infix fun String.le(v: Any) = Expr.Le(this, v)

infix fun String.like(pattern: String) = Expr.Like(this, pattern)
fun String.startsWith(prefix: String) = Expr.Like(this, "$prefix%")
fun String.endsWith(suffix: String) = Expr.Like(this, "%$suffix")
fun String.containsLike(substr: String) = Expr.Like(this, "%$substr%")

infix fun String.In(values: Iterable<Any?>) = Expr.InList(this, values.toList())
fun String.inList(vararg values: Any?) = Expr.InList(this, values.toList())

fun String.between(start: Any, end: Any) = Expr.Between(this, start, end)

fun String.isNull() = Expr.IsNull(this)
fun String.isNotNull() = Expr.IsNotNull(this)
