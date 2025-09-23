package org.repzone.multiplatform

import android.app.Application
import app.cash.sqldelight.db.SqlDriver
import com.repzone.database.AppDatabase
import com.repzone.database.Users
import com.repzone.database.orm.generated.OrmRegistryImpl
import com.repzone.di.DatabaseAndroidModule
import com.repzone.di.DatabaseModule
import com.repzone.orm.api.Orm
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
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
        val users = OrmRegistryImpl.byTypeId("Users") ?:error("Users meta not found")



        val rows = Orm.select(driver, users)


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