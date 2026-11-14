package com.fintech.moneymanager

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fintech.moneymanager.data.BudgetEntity
import com.fintech.moneymanager.data.TransactionEntity
import com.fintech.moneymanager.notifications.DailyReminderWorker
import com.fintech.moneymanager.notifications.NotificationHelper
import com.fintech.moneymanager.ui.MainViewModel
import java.util.concurrent.TimeUnit

val DarkBg = Color(0xFF0E1512)
val CardBg = Color(0xFF182420)
val EmeraldGreen = Color(0xFF10B981)
val EmeraldDark = Color(0xFF065F46)
val SlateGray = Color(0xFF94A3B8)

val USER_ACCOUNTS = listOf(
    "Union Bank *0007",
    "ICICI Bank XX002",
    "Slice xx9448",
    "Credit Card xx1009"
)

val BANK_ACCOUNTS_ONLY = listOf(
    "Union Bank *0007",
    "ICICI Bank XX002",
    "Slice xx9448"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannels(this)
        scheduleDailyReminder()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = EmeraldGreen,
                    background = DarkBg,
                    surface = CardBg
                )
            ) {
                MoneyManagerApp()
            }
        }
    }

    private fun scheduleDailyReminder() {
        val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_spending_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyManagerApp(vm: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddSheet by remember { mutableStateOf(false) }

    val transactions by vm.transactions.collectAsState()
    val pendingSms by vm.pendingTransactions.collectAsState()
    val budgets by vm.budgets.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    // INSTANT POP-UP DIALOG FOR EVERY NEW AUTO-DETECTED SMS TRANSACTION
    if (pendingSms.isNotEmpty()) {
        val currentPending = pendingSms.first()
        TransactionApprovalPopupDialog(
            item = currentPending,
            pendingCount = pendingSms.size,
            onApprove = { cat, acc, note ->
                vm.approvePendingTransaction(currentPending, cat, acc, note)
            },
            onDismiss = {
                vm.dismissPendingTransaction(currentPending)
            }
        )
    }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(Color.Transparent)
            ) {
                Surface(
                    color = CardBg,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavItem("Transactions", Icons.Default.ReceiptLong, selectedTab == 0) { selectedTab = 0 }
                        BottomNavItem("Analytics", Icons.Default.PieChart, selectedTab == 1) { selectedTab = 1 }
                        Spacer(modifier = Modifier.width(64.dp))
                        BottomNavItem("Budget & A/c", Icons.Default.AccountBalanceWallet, selectedTab == 2) { selectedTab = 2 }
                        BottomNavItem("Settings", Icons.Default.Settings, selectedTab == 3) { selectedTab = 3 }
                    }
                }

                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = EmeraldGreen,
                    contentColor = Color.Black,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction", modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> TransactionsScreen(transactions, pendingSms, vm)
                1 -> AnalyticsScreen(transactions)
                2 -> BudgetScreen(transactions, budgets, vm)
                3 -> SettingsScreen(vm)
            }
        }

        if (showAddSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddSheet = false },
                containerColor = CardBg
            ) {
                QuickAddTransactionSheet(
                    onSave = { amount, type, category, mode, account, note ->
                        vm.addTransaction(amount, type, category, mode, account, note)
                        showAddSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun TransactionApprovalPopupDialog(
    item: TransactionEntity,
    pendingCount: Int,
    onApprove: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCat by remember(item.id) { mutableStateOf(if (item.type == "INCOME") "Salary" else "Food") }
    var selectedAcc by remember(item.id) { mutableStateOf(item.bankName) }
    var note by remember(item.id) { mutableStateOf(item.merchantOrNote) }
    val categories = listOf("Food", "Bills", "Shopping", "Transport", "Investment", "Salary", "Transfer")

    AlertDialog(
        onDismissRequest = { },
        containerColor = CardBg,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("New Transaction Detected!", color = EmeraldGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (pendingCount > 1) {
                        Text("1 of $pendingCount", color = SlateGray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val sign = if (item.type == "INCOME") "+" else "-"
                val amtColor = if (item.type == "INCOME") EmeraldGreen else Color(0xFFF87171)
                Text("$sign₹${String.format("%,.2f", item.amount)}", color = amtColor, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item.rawSms?.let {
                    Surface(color = DarkBg, shape = RoundedCornerShape(8.dp)) {
                        Text(it, color = SlateGray, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }

                Text("Account Detected:", color = SlateGray, fontSize = 12.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    USER_ACCOUNTS.forEach { acc ->
                        FilterChip(
                            selected = selectedAcc == acc,
                            onClick = { selectedAcc = acc },
                            label = { Text(acc, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Select Category:", color = SlateGray, fontSize = 12.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCat == cat,
                            onClick = { selectedCat = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(selectedCat, selectedAcc, note) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black)
            ) {
                Text("Approve & Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Ignore", color = SlateGray)
            }
        }
    )
}

@Composable
fun BottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) EmeraldGreen else SlateGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    pendingSms: List<TransactionEntity>,
    vm: MainViewModel
) {
    var selectedAccountFilter by remember { mutableStateOf("All Accounts") }
    val filteredTx = if (selectedAccountFilter == "All Accounts") {
        transactions
    } else {
        transactions.filter { it.bankName == selectedAccountFilter }
    }

    val totalIncome = filteredTx.filter { it.type == "INCOME" && it.category != "CC Bill Repayment" }.sumOf { it.amount }
    val totalExpense = filteredTx.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val balance = totalIncome - totalExpense

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Money Manager", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        // Account Filter Bar
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf("All Accounts") + USER_ACCOUNTS).forEach { acc ->
                    FilterChip(
                        selected = selectedAccountFilter == acc,
                        onClick = { selectedAccountFilter = acc },
                        label = { Text(acc, fontSize = 12.sp) }
                    )
                }
            }
        }

        // Balance Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Net Balance ($selectedAccountFilter)", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("₹${String.format("%,.2f", balance)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Credited", color = EmeraldGreen, fontSize = 12.sp)
                            Text("+₹${String.format("%,.2f", totalIncome)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Debited / Spent", color = Color(0xFFF87171), fontSize = 12.sp)
                            Text("-₹${String.format("%,.2f", totalExpense)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        item {
            Text("Transaction History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        items(filteredTx) { tx ->
            TransactionRow(tx)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun TransactionRow(tx: TransactionEntity) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.merchantOrNote, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${tx.bankName} • ${tx.category} • ${tx.paymentMode}", color = SlateGray, fontSize = 12.sp)
            }
            val sign = if (tx.type == "INCOME") "+" else "-"
            val color = if (tx.type == "INCOME") EmeraldGreen else Color.White
            Text("$sign₹${String.format("%,.2f", tx.amount)}", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun AnalyticsScreen(transactions: List<TransactionEntity>) {
    val expenses = transactions.filter { it.type == "EXPENSE" && it.category != "CC Bill Repayment" }
    val totalSpend = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
    val byAccount = expenses.groupBy { it.bankName }.mapValues { it.value.sumOf { tx -> tx.amount } }
    val byCategory = expenses.groupBy { it.category }.mapValues { it.value.sumOf { tx -> tx.amount } }
    val chartColors = listOf(EmeraldGreen, Color(0xFF38BDF8), Color(0xFFF59E0B), Color(0xFFA855F7), Color(0xFFF43F5E))

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Spending Analytics", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                        Canvas(modifier = Modifier.size(170.dp)) {
                            var startAngle = -90f
                            byCategory.entries.forEachIndexed { idx, entry ->
                                val sweep = ((entry.value / totalSpend) * 360f).toFloat()
                                drawArc(
                                    color = chartColors[idx % chartColors.size],
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = 34f, cap = StrokeCap.Round)
                                )
                                startAngle += sweep
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Total Spend", color = SlateGray, fontSize = 12.sp)
                            Text("₹${String.format("%,.0f", expenses.sumOf { it.amount })}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("Spend by Account", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(byAccount.entries.toList()) { entry ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entry.key, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("₹${String.format("%,.2f", entry.value)}", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Text("Spend by Category", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(byCategory.entries.toList()) { entry ->
            val pct = ((entry.value / totalSpend) * 100).toInt()
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entry.key, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("₹${String.format("%,.2f", entry.value)} ($pct%)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BudgetScreen(
    transactions: List<TransactionEntity>,
    budgets: List<BudgetEntity>,
    vm: MainViewModel
) {
    var showRepayDialog by remember { mutableStateOf(false) }

    // Calculate Credit Card xx1009 Due
    val ccSpent = transactions.filter { it.bankName == "Credit Card xx1009" && it.type == "EXPENSE" }.sumOf { it.amount }
    val ccRepaid = transactions.filter { it.bankName == "Credit Card xx1009" && it.type == "INCOME" }.sumOf { it.amount }
    val ccOutstanding = (ccSpent - ccRepaid).coerceAtLeast(0.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Accounts & Budgets", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Separate account balances, credit card repayment & limits", color = SlateGray, fontSize = 13.sp)
        }

        // CREDIT CARD xx1009 REPAYMENT CARD
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1A29)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Credit Card xx1009", color = Color(0xFFF472B6), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Total Spent: ₹${String.format("%,.2f", ccSpent)}", color = SlateGray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Outstanding Due: ₹${String.format("%,.2f", ccOutstanding)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showRepayDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay Credit Card Bill (Repayment)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SEPARATE BANK ACCOUNT BALANCES & SPENDING
        item {
            Text("Bank Account Operations", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(BANK_ACCOUNTS_ONLY) { accName ->
            val accIncome = transactions.filter { it.bankName == accName && it.type == "INCOME" }.sumOf { it.amount }
            val accExpense = transactions.filter { it.bankName == accName && it.type == "EXPENSE" }.sumOf { it.amount }
            val accBalance = accIncome - accExpense
            val accBudget = budgets.find { it.category == accName }?.monthlyLimit ?: 20000.0
            val progress = (accExpense / accBudget).toFloat().coerceIn(0f, 1f)

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(accName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Bal: ₹${String.format("%,.2f", accBalance)}", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "In: +₹${accIncome.toInt()}  |  Out: -₹${accExpense.toInt()} (Limit: ₹${accBudget.toInt()})",
                        color = SlateGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = if (progress >= 0.8f) Color(0xFFF43F5E) else EmeraldGreen,
                        trackColor = DarkBg,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        item {
            Text("Category Monthly Budgets", color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        val categoryBudgets = budgets.filter { it.category !in USER_ACCOUNTS }
        items(categoryBudgets) { budget ->
            val spent = transactions.filter { it.type == "EXPENSE" && it.category.equals(budget.category, true) }.sumOf { it.amount }
            val progress = (spent / budget.monthlyLimit).toFloat().coerceIn(0f, 1f)
            val barColor = if (progress >= 0.8f) Color(0xFFF43F5E) else EmeraldGreen

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(budget.category, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("₹${spent.toInt()} / ₹${budget.monthlyLimit.toInt()}", color = SlateGray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = barColor,
                        trackColor = DarkBg,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showRepayDialog) {
        var repayAmount by remember { mutableStateOf(if (ccOutstanding > 0) ccOutstanding.toString() else "") }
        var fromAccount by remember { mutableStateOf(BANK_ACCOUNTS_ONLY.first()) }

        AlertDialog(
            onDismissRequest = { showRepayDialog = false },
            containerColor = CardBg,
            title = { Text("Credit Card xx1009 Repayment", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select which bank account you are paying from:", color = SlateGray, fontSize = 13.sp)
                    BANK_ACCOUNTS_ONLY.forEach { acc ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { fromAccount = acc }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = fromAccount == acc, onClick = { fromAccount = acc })
                            Text(acc, color = Color.White, fontSize = 14.sp)
                        }
                    }
                    OutlinedTextField(
                        value = repayAmount,
                        onValueChange = { repayAmount = it },
                        label = { Text("Repayment Amount (₹)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = repayAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            vm.recordCreditCardRepayment(amt, fromAccount)
                            showRepayDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black)
                ) {
                    Text("Confirm Repayment", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRepayDialog = false }) {
                    Text("Cancel", color = SlateGray)
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val userSampleMessages = listOf(
        "SB A/c *0007 Credited for Rs:8000.00",
        "Union Bank of India A/c*0007 Dr Rs5926.00",
        "Union Bank of India A/c *0007 Debited Rs:335.00 on 30-10-2026",
        "ICICI Bank Acct XX002 debited for Rs 9445.00 on 30-Oct-26",
        "Dear Customer, Acct XX002 is credited with Rs 450.00 on 25-Oct-26 from",
        "Rs. 3,015.85 spent on your credit card xx1009",
        "Rs. 18,000 received in slice A/c xx9448 on 20-Oct-26 from",
        "Rs. 3,740 sent from a/c xx9448 on 22-Oct-26 to"
    )
    var sampleSms by remember { mutableStateOf(userSampleMessages[0]) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings & Live SMS Tester", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Enable Auto-Detection on Nothing Phone", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    Text("Allow Notification Access so incoming Bank & UPI messages trigger the Pop-Up automatically:", color = SlateGray, fontSize = 12.sp)
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Notification Access Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Test Your Exact Bank Messages", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    Text("Tap any of your bank messages below to test the Pop-Up & Account Parser:", color = SlateGray, fontSize = 12.sp)

                    userSampleMessages.forEach { msg ->
                        Surface(
                            color = DarkBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { sampleSms = msg }
                        ) {
                            Text(msg, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                        }
                    }

                    OutlinedTextField(
                        value = sampleSms,
                        onValueChange = { sampleSms = it },
                        label = { Text("Selected SMS") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.simulateBankSms(sampleSms) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Trigger Pop-Up & Notification Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun QuickAddTransactionSheet(
    onSave: (Double, String, String, String, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Food") }
    var paymentMode by remember { mutableStateOf("UPI") }
    var selectedAccount by remember { mutableStateOf(USER_ACCOUNTS[0]) }
    var note by remember { mutableStateOf("") }

    val categories = listOf("Food", "Bills", "Shopping", "Transport", "Investment", "Salary")
    val modes = listOf("UPI", "Credit Card", "Cash", "Bank Transfer")

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Quick Add Transaction", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = type == "EXPENSE", onClick = { type = "EXPENSE" }, label = { Text("Expense") })
            FilterChip(selected = type == "INCOME", onClick = { type = "INCOME" }, label = { Text("Income") })
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (₹)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Select Account", color = SlateGray, fontSize = 13.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            USER_ACCOUNTS.forEach { acc ->
                FilterChip(
                    selected = selectedAccount == acc,
                    onClick = {
                        selectedAccount = acc
                        if (acc == "Credit Card xx1009") paymentMode = "Credit Card"
                    },
                    label = { Text(acc, fontSize = 12.sp) }
                )
            }
        }

        Text("Category", color = SlateGray, fontSize = 13.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat, fontSize = 12.sp) })
            }
        }

        Text("Payment Mode", color = SlateGray, fontSize = 13.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modes.forEach { mode ->
                FilterChip(selected = paymentMode == mode, onClick = { paymentMode = mode }, label = { Text(mode, fontSize = 12.sp) })
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Merchant / Note") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                if (amt > 0) {
                    onSave(amt, type, category, paymentMode, selectedAccount, note)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Save Transaction", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
