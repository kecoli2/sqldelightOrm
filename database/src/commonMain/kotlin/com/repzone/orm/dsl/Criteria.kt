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
    data class IsNull(val col: String) : Expr
    data class IsNotNull(val col: String) : Expr
    data class And(val items: List<Expr>) : Expr
    data class Or(val items: List<Expr>) : Expr
}

fun and(vararg e: Expr) = Expr.And(e.toList())
fun or(vararg e: Expr)  = Expr.Or(e.toList())