package com.fintech.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 2, exportSchema = false)
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
                        seedStarterData(context)
                    }
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        seedStarterData(context)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedStarterData(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).appDao()
                // Category & Account Monthly Budgets
                dao.upsertBudget(BudgetEntity("Food", 12000.0))
                dao.upsertBudget(BudgetEntity("Bills", 15000.0))
                dao.upsertBudget(BudgetEntity("Shopping", 10000.0))
                dao.upsertBudget(BudgetEntity("Union Bank *0007", 25000.0))
                dao.upsertBudget(BudgetEntity("ICICI Bank XX002", 20000.0))
                dao.upsertBudget(BudgetEntity("Slice xx9448", 15000.0))
                dao.upsertBudget(BudgetEntity("Credit Card xx1009", 15000.0))

                // Starter transactions based on your exact SMS messages
                dao.insertTransaction(
                    TransactionEntity(
                        amount = 8000.0,
                        type = "INCOME",
                        category = "Salary",
                        paymentMode = "Bank Transfer",
                        merchantOrNote = "SB A/c *0007 Credited",
                        bankName = "Union Bank *0007",
                        isPendingApproval = false
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        amount = 18000.0,
                        type = "INCOME",
                        category = "Salary",
                        paymentMode = "UPI",
                        merchantOrNote = "Received in Slice A/c xx9448",
                        bankName = "Slice xx9448",
                        isPendingApproval = false
                    )
                )
                dao.insertTransaction(
                    TransactionEntity(
                        amount = 3015.85,
                        type = "EXPENSE",
                        category = "Shopping",
                        paymentMode = "Credit Card",
                        merchantOrNote = "Credit Card Spend xx1009",
                        bankName = "Credit Card xx1009",
                        isPendingApproval = false
                    )
                )
                // One pending SMS so you immediately see the Pop-Up Dialog on launch!
                dao.insertTransaction(
                    TransactionEntity(
                        amount = 5926.0,
                        type = "EXPENSE",
                        category = "Bills",
                        paymentMode = "Bank Transfer",
                        merchantOrNote = "Union Bank *0007 Debit",
                        bankName = "Union Bank *0007",
                        isPendingApproval = true,
                        rawSms = "Union Bank of India A/c*0007 Dr Rs5926.00"
                    )
                )
            }
        }
    }
}
