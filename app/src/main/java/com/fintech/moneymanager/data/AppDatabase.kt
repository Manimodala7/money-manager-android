package com.fintech.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [TransactionEntity::class, BudgetEntity::class, AccountBalanceEntity::class],
    version = 4,
    exportSchema = false
)
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
                        seedDefaults(context)
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        seedDefaults(context)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedDefaults(context: Context) {
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

                // Initialize starting balances to 0.0 (User can edit Current Balance anytime!)
                dao.upsertAccountBalance(AccountBalanceEntity("Union Bank *0007", 0.0))
                dao.upsertAccountBalance(AccountBalanceEntity("ICICI Bank XX002", 0.0))
                dao.upsertAccountBalance(AccountBalanceEntity("Slice xx9448", 0.0))
            }
        }
    }
}
