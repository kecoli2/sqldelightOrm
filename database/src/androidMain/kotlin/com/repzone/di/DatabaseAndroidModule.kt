package com.repzone.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.repzone.database.AppDatabase
import com.repzone.driver.LoggingSqlDriver
import com.repzone.orm.logging.DefaultSqlLogger
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val DatabaseAndroidModule = module {
    single<SqlDriver> {
        // Base driver
        val base = AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = androidContext(),
            name = "repzone.db"
        )
        LoggingSqlDriver(delegate = base)
    }
}