package com.repzone.orm.runtime


import app.cash.sqldelight.db.QueryResult        // 2.x: doğru paket
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.repzone.orm.meta.ColumnAffinity
import com.repzone.orm.meta.TableMeta

// ---- Helpers ----

private fun SqlPreparedStatement.bindAny(index: Int, v: Any?) {
    when (v) {
        null        -> bindAny(index, null)                 // null için bindNull; rekürsiyon yok
        is Long     -> bindLong(index, v)
        is Int      -> bindLong(index, v.toLong())
        is Short    -> bindLong(index, v.toLong())
        is Byte     -> bindLong(index, v.toLong())
        is Boolean  -> bindLong(index, if (v) 1L else 0L)
        is Double   -> bindDouble(index, v)
        is Float    -> bindDouble(index, v.toDouble())
        is String   -> bindString(index, v)
        is ByteArray-> bindBytes(index, v)
        else        -> bindString(index, v.toString())
    }
}

private fun SqlCursor.readByAffinity(idx: Int, affinity: ColumnAffinity): Any? = when (affinity) {
    ColumnAffinity.INTEGER -> getLong(idx)
    ColumnAffinity.REAL    -> getDouble(idx)
    ColumnAffinity.TEXT    -> getString(idx)
    ColumnAffinity.BLOB    -> getBytes(idx)
    ColumnAffinity.NUMERIC -> getString(idx)   // karma tiplere güvenli yaklaşım
    ColumnAffinity.UNKNOWN -> getString(idx)
}

// ---- Public ----

fun executeSelect(
    driver: SqlDriver,
    table: TableMeta,
    stmt: SqlStatement
): List<DataRow> {
    val result: QueryResult<List<DataRow>> = driver.executeQuery(
        identifier = null,
        sql = stmt.sql,
        mapper = { cursor: SqlCursor ->
            val rows = ArrayList<DataRow>()
            val cols = table.columns
            while (cursor.next().value) {        // 2.x: QueryResult<Boolean> -> .value
                val map = LinkedHashMap<String, Any?>()
                for (i in cols.indices) {
                    val c = cols[i]
                    map[c.name] = cursor.readByAffinity(i, c.affinity)
                }
                rows += DataRow(map)
            }
            QueryResult.Value(rows)              // <-- ÖNEMLİ: QueryResult.Value dön
        },
        parameters = stmt.args.size,
        binders = {
            // 2.x: receiver'lı lambda; parametre indexleri 0-based
            stmt.args.forEachIndexed { i, v -> bindAny(i, v) }
        }
    )
    return result.value
}