package com.fintech.moneymanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fintech.moneymanager.data.AppDatabase
import com.fintech.moneymanager.data.BudgetEntity
import com.fintech.moneymanager.data.TransactionEntity
import com.fintech.moneymanager.notifications.NotificationHelper
import com.fintech.moneymanager.sms.BankSmsParser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).appDao()

    val transactions = dao.getApprovedTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingTransactions = dao.getPendingSmsTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgets = dao.getAllBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        paymentMode: String,
        note: String
    ) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = type,
                    category = category,
                    paymentMode = paymentMode,
                    merchantOrNote = note.ifBlank { category },
                    isPendingApproval = false
                )
            )
            checkBudgetThreshold(category)
        }
    }

    fun approvePendingTransaction(item: TransactionEntity, selectedCategory: String) {
        viewModelScope.launch {
            dao.updateTransaction(
                item.copy(
                    category = selectedCategory,
                    isPendingApproval = false
                )
            )
            checkBudgetThreshold(selectedCategory)
        }
    }

    fun dismissPendingTransaction(item: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(item)
        }
    }

    fun updateBudget(category: String, limit: Double) {
        viewModelScope.launch {
            dao.upsertBudget(BudgetEntity(category, limit))
        }
    }

    fun simulateBankSms(rawMessage: String) {
        viewModelScope.launch {
            val parsed = BankSmsParser.parse("VM-HDFCBK", rawMessage)
            if (parsed != null) {
                dao.insertTransaction(
                    TransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        category = "Food",
                        paymentMode = parsed.paymentMode,
                        merchantOrNote = parsed.merchant,
                        bankName = parsed.bankName,
                        isPendingApproval = true,
                        rawSms = rawMessage
                    )
                )
                NotificationHelper.sendSmsDetectedNotification(
                    context = getApplication(),
                    amount = parsed.amount,
                    type = parsed.type,
                    merchant = parsed.merchant,
                    bank = parsed.bankName
                )
            }
        }
    }

    private suspend fun checkBudgetThreshold(category: String) {
        val totalSpent = dao.getTotalSpentForCategory(category) ?: 0.0
        val budgetList = budgets.value
        val limit = budgetList.find { it.category.equals(category, ignoreCase = true) }?.monthlyLimit ?: return
        if (limit > 0 && totalSpent >= limit * 0.8) {
            NotificationHelper.sendBudgetWarning(getApplication(), category, totalSpent, limit)
        }
    }
}
