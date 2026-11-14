package com.fintech.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "money_manager_db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaultBudgetsOnly(context)
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        seedDefaultBudgetsOnly(context)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        // Seeds ONLY default monthly budget limits — 0 fake transactions!
        private fun seedDefaultBudgetsOnly(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).appDao()
                dao.upsertBudget(BudgetEntity("Union Bank *0007", 25000.0))
                dao.upsertBudget(BudgetEntity("ICICI Bank XX002", 20000.0))
                dao.upsertBudget(BudgetEntity("Slice xx9448", 15000.0))
                dao.upsertBudget(BudgetEntity("Credit Card xx1009", 15000.0))

                dao.upsertBudget(BudgetEntity("Food & Dining", 8000.0))
                dao.upsertBudget(BudgetEntity("Groceries", 6000.0))
                dao.upsertBudget(BudgetEntity("Shopping", 10000.0))
                dao.upsertBudget(BudgetEntity("Transport & Fuel", 5000.0))
                dao.upsertBudget(BudgetEntity("Bills & Utilities", 10000.0))
                dao.upsertBudget(BudgetEntity("Investments", 15000.0))
            }
        }
    }
}
