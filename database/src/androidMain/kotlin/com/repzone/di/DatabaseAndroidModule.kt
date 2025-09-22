package com.repzone.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.repzone.database.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val DatabaseAndroidModule = module {
    single<SqlDriver> {
        AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = androidContext(),
            name = "repzone.db"
        )
    }
}