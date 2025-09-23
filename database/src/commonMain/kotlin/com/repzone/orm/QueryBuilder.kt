package com.repzone.orm

data class WhereCondition(
    val field: String,
    val operator: String = "=",
    val value: Any?,
    val connector: String = "AND"
)

data class OrderCondition(
    val field: String,
    val direction: String = "ASC"
)

data class SelectOptions(
    val where: List<WhereCondition> = emptyList(),
    val orderBy: List<OrderCondition> = emptyList(),
    val groupBy: List<String> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null
)

class SelectBuilder {
    private val whereConditions = mutableListOf<WhereCondition>()
    private val orderConditions = mutableListOf<OrderCondition>()
    private val groupByFields = mutableListOf<String>()
    private var limitValue: Int? = null
    private var offsetValue: Int? = null

    fun where(field: String, operator: String = "=", value: Any?, connector: String = "AND"): SelectBuilder {
        whereConditions.add(WhereCondition(field, operator, value, connector))
        return this
    }

    fun where(field: String, value: Any?): SelectBuilder = where(field, "=", value)
    fun whereIn(field: String, values: List<Any?>): SelectBuilder = where(field, "IN", values)
    fun whereLike(field: String, pattern: String): SelectBuilder = where(field, "LIKE", pattern)

    fun orderBy(field: String, direction: String = "ASC"): SelectBuilder {
        orderConditions.add(OrderCondition(field, direction))
        return this
    }

    fun orderByAsc(field: String): SelectBuilder = orderBy(field, "ASC")
    fun orderByDesc(field: String): SelectBuilder = orderBy(field, "DESC")

    fun groupBy(vararg fields: String): SelectBuilder {
        groupByFields.addAll(fields)
        return this
    }

    fun limit(count: Int): SelectBuilder {
        limitValue = count
        return this
    }

    fun offset(count: Int): SelectBuilder {
        offsetValue = count
        return this
    }

    internal fun build(): SelectOptions {
        return SelectOptions(
            where = whereConditions,
            orderBy = orderConditions,
            groupBy = groupByFields,
            limit = limitValue,
            offset = offsetValue
        )
    }
}
