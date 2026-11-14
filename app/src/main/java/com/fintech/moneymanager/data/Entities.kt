package com.fintech.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "EXPENSE" or "INCOME"
    val category: String,
    val paymentMode: String, // "UPI", "Card", "Cash", "Bank Transfer"
    val merchantOrNote: String,
    val bankName: String = "Manual",
    val timestamp: Long = System.currentTimeMillis(),
    val isPendingApproval: Boolean = false, // True when auto-detected from SMS
    val rawSms: String? = null
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimit: Double
)
