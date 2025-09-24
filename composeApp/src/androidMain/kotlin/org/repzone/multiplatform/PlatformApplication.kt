package org.repzone.multiplatform

import android.app.Application
import android.widget.TableRow
import app.cash.sqldelight.db.SqlDriver
import com.repzone.database.AppDatabase
import com.repzone.database.Users
import com.repzone.di.DatabaseAndroidModule
import com.repzone.di.DatabaseModule
import com.repzone.orm.api.Entity
import com.repzone.orm.dsl.*
import com.repzone.orm.meta.TableMeta
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

        // Sorudaki ifade:
        val c1 = group { ("userName" eq "salih") and ("id" eq 2) } or ("userName".isNull()) or ("id" eq 1)

        // NOT ve BETWEEN:
        val c2 = !("deleted".isNotNull()) and ("age".between(18, 30))

        // IN liste:
        val c3 = ("status" In listOf("active", "pending")) and ("name".containsLike("ali"))

        // startsWith/endsWith:
        val c4 = ("email".startsWith("info@")) or ("email".endsWith(".org"))

        // where{} şeker kullanımı:
        val c = where {
            group { ("userName" eq "salih") and ("id" eq 2) } or ("userName".isNull()) or ("id" eq 1)
        }

        var sss : TableMeta? = null

        val db: AppDatabase = getKoin().get()
        //val sss = Entity.select<UsersRow>()
        val driver : SqlDriver = getKoin().get()


        val p = Entity.page<Users>(
            driver = driver,
            criteria = and(
                Expr.IsNull("deleted_at"),
                Expr.Like("userName", "%a%")

            ),
            order = listOf(OrderSpec("id", asc = false)),
            page = 1,
            size = 20,
            includeTotal = true
        )

        try {
            val userList11 = Entity.select<Users>(driver, criteria = c1)

            val userList12 = Entity.select<Users>(driver, criteria = c3)

            if(1==1){

            }


        }catch (ex: Exception){
            ex.printStackTrace()
        }


        val userList = Entity.select<Users>(driver, criteria = and(
            Expr.Eq("userName", "salih")
        ))

        try {

            val userList2 = Entity.select<Users>(driver, criteria = and(
                Expr.Eq("userName2", "salih")
            ))

            if(1==1){

            }

        }catch (ex: Exception){
            ex.printStackTrace()
        }




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