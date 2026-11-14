package com.fintech.moneymanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [TransactionEntity::class, BudgetEntity::class], version = 1, exportSchema = false)
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
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val dao = getDatabase(context).appDao()
                            // Default starter budgets
                            dao.upsertBudget(BudgetEntity("Food", 15000.0))
                            dao.upsertBudget(BudgetEntity("Bills", 20000.0))
                            dao.upsertBudget(BudgetEntity("Shopping", 10000.0))
                            dao.upsertBudget(BudgetEntity("Investment", 15000.0))
                            dao.upsertBudget(BudgetEntity("Transport", 5000.0))

                            // Starter sample transactions
                            dao.insertTransaction(
                                TransactionEntity(
                                    amount = 65000.0,
                                    type = "INCOME",
                                    category = "Salary",
                                    paymentMode = "Bank Transfer",
                                    merchantOrNote = "Monthly Salary Credit",
                                    bankName = "HDFC",
                                    isPendingApproval = false
                                )
                            )
                            dao.insertTransaction(
                                TransactionEntity(
                                    amount = 3450.0,
                                    type = "EXPENSE",
                                    category = "Food",
                                    paymentMode = "UPI",
                                    merchantOrNote = "Grocery & Supermart",
                                    bankName = "SBI",
                                    isPendingApproval = false
                                )
                            )
                            dao.insertTransaction(
                                TransactionEntity(
                                    amount = 450.0,
                                    type = "EXPENSE",
                                    category = "Food",
                                    paymentMode = "UPI",
                                    merchantOrNote = "Starbucks Coffee (Auto-Detected)",
                                    bankName = "HDFC Bank",
                                    isPendingApproval = true,
                                    rawSms = "Rs.450.00 debited from A/c XX4821 via UPI to STARBUCKS."
                                )
                            )
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
