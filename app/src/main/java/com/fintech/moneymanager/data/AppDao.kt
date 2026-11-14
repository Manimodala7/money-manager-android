package com.fintech.moneymanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM transactions WHERE isPendingApproval = 0 ORDER BY timestamp DESC")
    fun getApprovedTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isPendingApproval = 1 ORDER BY timestamp DESC")
    fun getPendingSmsTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("SELECT * FROM account_balances")
    fun getAllAccountBalances(): Flow<List<AccountBalanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccountBalance(accountBalance: AccountBalanceEntity)

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND (category = :category OR bankName = :category) AND isPendingApproval = 0")
    suspend fun getTotalSpentForCategory(category: String): Double?
}
