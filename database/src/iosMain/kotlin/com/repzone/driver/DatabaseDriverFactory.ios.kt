package com.repzone.driver

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.repzone.database.AppDatabase

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(schema = AppDatabase.Schema, name = "app.db")
}
