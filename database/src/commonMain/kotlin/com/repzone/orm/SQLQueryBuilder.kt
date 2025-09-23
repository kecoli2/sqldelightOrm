package com.repzone.orm

class SQLQueryBuilder {

    fun buildSelectQuery(
        tableName: String,
        fields: List<String>,
        options: SelectOptions
    ): Pair<String, List<Any?>> {
        val parameters = mutableListOf<Any?>()

        val query = buildString {
            append("SELECT ${fields.joinToString(", ")} FROM $tableName")

            if (options.where.isNotEmpty()) {
                append(" WHERE ")
                append(buildWhereClause(options.where, parameters))
            }

            if (options.groupBy.isNotEmpty()) {
                append(" GROUP BY ${options.groupBy.joinToString(", ")}")
            }

            if (options.orderBy.isNotEmpty()) {
                append(" ORDER BY ")
                append(options.orderBy.joinToString(", ") { "${it.field} ${it.direction}" })
            }

            options.limit?.let { append(" LIMIT $it") }
            options.offset?.let { append(" OFFSET $it") }
        }

        return query to parameters
    }

    private fun buildWhereClause(conditions: List<WhereCondition>, parameters: MutableList<Any?>): String {
        return conditions.mapIndexed { index, condition ->
            val clause = buildConditionClause(condition, parameters)
            if (index == 0) clause else "${condition.connector} $clause"
        }.joinToString(" ")
    }

    private fun buildConditionClause(condition: WhereCondition, parameters: MutableList<Any?>): String {
        return when (condition.operator.uppercase()) {
            "IN" -> {
                val values = condition.value as? List<*> ?: listOf(condition.value)
                parameters.addAll(values)
                "${condition.field} IN (${values.joinToString(",") { "?" }})"
            }
            "BETWEEN" -> {
                val values = condition.value as? List<*> ?: throw IllegalArgumentException("BETWEEN requires list of 2 values")
                parameters.addAll(values)
                "${condition.field} BETWEEN ? AND ?"
            }
            else -> {
                parameters.add(condition.value)
                "${condition.field} ${condition.operator} ?"
            }
        }
    }
}