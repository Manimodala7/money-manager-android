package com.fintech.moneymanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fintech.moneymanager.data.AppDatabase
import com.fintech.moneymanager.data.BudgetEntity
import com.fintech.moneymanager.data.CategoryCatalog
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
        subCategory: String,
        paymentMode: String,
        accountName: String,
        note: String
    ) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = type,
                    category = category,
                    subCategory = subCategory,
                    paymentMode = paymentMode,
                    merchantOrNote = note.ifBlank { "$subCategory ($category)" },
                    bankName = accountName,
                    isPendingApproval = false
                )
            )
            checkBudgetThreshold(category)
            checkBudgetThreshold(accountName)
        }
    }

    fun editTransaction(updated: TransactionEntity) {
        viewModelScope.launch {
            dao.updateTransaction(updated)
            checkBudgetThreshold(updated.category)
            checkBudgetThreshold(updated.bankName)
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(tx)
        }
    }

    fun clearAllTransactions() {
        viewModelScope.launch {
            dao.clearAllTransactions()
        }
    }

    fun recordCreditCardRepayment(amount: Double, fromBankAccount: String) {
        viewModelScope.launch {
            dao.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = "EXPENSE",
                    category = "Self Transfer",
                    subCategory = "CC Bill Repayment",
                    paymentMode = "Bank Transfer",
                    merchantOrNote = "Paid Credit Card xx1009 from $fromBankAccount",
                    bankName = fromBankAccount,
                    isPendingApproval = false
                )
            )
            dao.insertTransaction(
                TransactionEntity(
                    amount = amount,
                    type = "INCOME",
                    category = "Self Transfer",
                    subCategory = "CC Bill Repayment",
                    paymentMode = "Credit Card",
                    merchantOrNote = "Repayment from $fromBankAccount",
                    bankName = "Credit Card xx1009",
                    isPendingApproval = false
                )
            )
        }
    }

    fun approvePendingTransaction(
        item: TransactionEntity,
        selectedCategory: String,
        selectedSubCategory: String,
        selectedAccount: String,
        customNote: String
    ) {
        viewModelScope.launch {
            dao.updateTransaction(
                item.copy(
                    category = selectedCategory,
                    subCategory = selectedSubCategory,
                    bankName = selectedAccount,
                    merchantOrNote = customNote.ifBlank { "$selectedSubCategory - ${item.merchantOrNote}" },
                    isPendingApproval = false
                )
            )
            checkBudgetThreshold(selectedCategory)
            checkBudgetThreshold(selectedAccount)
        }
    }

    fun dismissPendingTransaction(item: TransactionEntity) {
        viewModelScope.launch {
            dao.deleteTransaction(item)
        }
    }

    fun updateBudget(categoryOrAccount: String, limit: Double) {
        viewModelScope.launch {
            dao.upsertBudget(BudgetEntity(categoryOrAccount, limit))
        }
    }

    fun simulateBankSms(rawMessage: String) {
        viewModelScope.launch {
            val parsed = BankSmsParser.parse("BANK-SMS", rawMessage)
            if (parsed != null) {
                val (autoCat, autoSub) = CategoryCatalog.autoDetectCategory(rawMessage, parsed.type)
                dao.insertTransaction(
                    TransactionEntity(
                        amount = parsed.amount,
                        type = parsed.type,
                        category = autoCat,
                        subCategory = autoSub,
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
                    merchant = "${parsed.merchant} ($autoSub)",
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
