package org.repzone.multiplatform

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.repzone.database.AppDatabase
import com.repzone.database.Users
import com.repzone.di.DatabaseAndroidModule
import com.repzone.di.DatabaseModule
import com.repzone.orm.core.Entity
import org.koin.android.ext.koin.androidContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.context.loadKoinModules
import org.koin.core.parameter.parametersOf
import org.koin.java.KoinJavaComponent.getKoin

class PlatformApplication: Application() {

    //region Field
    //endregion

    //region Properties
    //endregion

    //region Constructor
    //endregion

    //region Public Method
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PlatformApplication)
            modules(
                DatabaseModule,
                DatabaseAndroidModule
            )
        }

        val db: AppDatabase = getKoin().get()
        //val sss = Entity.select<UsersRow>()
        val driver : SqlDriver = getKoin().get()
        val lst = Entity.select<Users>(driver)

        db.userQueries.transaction {
            //db.userQueries.insertOrReplace(Users(1,"dsdada",null,null,null,null,null))
            val lst = db.userQueries.selectById(1).executeAsOneOrNull()



            if(1==1){

            }
        }

    }
    //endregion

    //region Protected Method
    //endregion

    //region Private Method
    //endregion
}