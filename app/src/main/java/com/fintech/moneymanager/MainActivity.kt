package com.fintech.moneymanager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transactions, 1: Analytics, 2: Budget, 3: Settings
    var showAddSheet by remember { mutableStateOf(false) }

    val transactions by vm.transactions.collectAsState()
    val pendingSms by vm.pendingTransactions.collectAsState()
    val budgets by vm.budgets.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            // Custom 5-Slot Bottom Bar: Transactions | Analytics | Add (Hover) | Budget | Settings
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
                        Spacer(modifier = Modifier.width(64.dp)) // Space for Hover Center Button
                        BottomNavItem("Budget", Icons.Default.AccountBalanceWallet, selectedTab == 2) { selectedTab = 2 }
                        BottomNavItem("Settings", Icons.Default.Settings, selectedTab == 3) { selectedTab = 3 }
                    }
                }

                // Hovering Center Quick-Add Button
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
                    onSave = { amount, type, category, mode, note ->
                        vm.addTransaction(amount, type, category, mode, note)
                        showAddSheet = false
                    }
                )
            }
        }
    }
}

@Composable
fun BottomNavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) EmeraldGreen else SlateGray
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp)
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
    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
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

        // Balance Summary Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EmeraldDark),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Balance (This Month)", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("₹${String.format("%,.2f", balance)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", color = EmeraldGreen, fontSize = 12.sp)
                            Text("+₹${String.format("%,.0f", totalIncome)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Expenses", color = Color(0xFFF87171), fontSize = 12.sp)
                            Text("-₹${String.format("%,.0f", totalExpense)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
        }

        // Pending Bank SMS Auto-Detected Section
        if (pendingSms.isNotEmpty()) {
            item {
                Text(
                    "Pending SMS Approval (${pendingSms.size})",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            items(pendingSms) { item ->
                PendingSmsCard(item, vm)
            }
        }

        item {
            Text("Transaction History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        items(transactions) { tx ->
            TransactionRow(tx)
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
fun PendingSmsCard(item: TransactionEntity, vm: MainViewModel) {
    var selectedCat by remember { mutableStateOf("Food") }
    val categories = listOf("Food", "Bills", "Shopping", "Transport", "Investment")

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Auto-Detected (${item.bankName})", color = EmeraldGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("₹${item.amount}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.merchantOrNote, color = Color.White, fontSize = 14.sp)
            item.rawSms?.let {
                Text(it, color = SlateGray, fontSize = 11.sp, maxLines = 2)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCat == cat,
                        onClick = { selectedCat = cat },
                        label = { Text(cat, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.approvePendingTransaction(item, selectedCat) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black)
                ) {
                    Text("Approve & Save", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { vm.dismissPendingTransaction(item) }) {
                    Text("Dismiss", color = SlateGray)
                }
            }
        }
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
            Column {
                Text(tx.merchantOrNote, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${tx.category} • ${tx.paymentMode}", color = SlateGray, fontSize = 12.sp)
            }
            val sign = if (tx.type == "INCOME") "+" else "-"
            val color = if (tx.type == "INCOME") EmeraldGreen else Color.White
            Text("$sign₹${String.format("%,.0f", tx.amount)}", color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun AnalyticsScreen(transactions: List<TransactionEntity>) {
    val expenses = transactions.filter { it.type == "EXPENSE" }
    val totalSpend = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
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
                            Text("Monthly Spend", color = SlateGray, fontSize = 12.sp)
                            Text("₹${String.format("%,.0f", expenses.sumOf { it.amount })}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
                    Text("₹${String.format("%,.0f", entry.value)} ($pct%)", color = EmeraldGreen, fontWeight = FontWeight.Bold)
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
    val expenses = transactions.filter { it.type == "EXPENSE" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Monthly Budgets", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Alerts trigger automatically at 80% usage", color = SlateGray, fontSize = 13.sp)
        }

        items(budgets) { budget ->
            val spent = expenses.filter { it.category.equals(budget.category, true) }.sumOf { it.amount }
            val progress = (spent / budget.monthlyLimit).toFloat().coerceIn(0f, 1f)
            val barColor = if (progress >= 0.8f) Color(0xFFF43F5E) else EmeraldGreen

            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(budget.category, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("₹${spent.toInt()} / ₹${budget.monthlyLimit.toInt()}", color = SlateGray, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = barColor,
                        trackColor = DarkBg,
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var sampleSms by remember {
        mutableStateOf("Rs.850.00 debited from A/c XX4821 on 14-Nov-26 to VPA swiggy@ybl. UPI Ref 63192831.")
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & SMS Tester", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Test Bank SMS Auto-Detection", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                Text("Paste a sample bank SMS below to test instant notification & auto-parsing:", color = SlateGray, fontSize = 12.sp)
                OutlinedTextField(
                    value = sampleSms,
                    onValueChange = { sampleSms = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { vm.simulateBankSms(sampleSms) },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simulate Incoming Bank SMS", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QuickAddTransactionSheet(
    onSave: (Double, String, String, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableStateOf("Food") }
    var paymentMode by remember { mutableStateOf("UPI") }
    var note by remember { mutableStateOf("") }

    val categories = listOf("Food", "Bills", "Shopping", "Transport", "Investment", "Salary")
    val modes = listOf("UPI", "Card", "Cash", "Bank Transfer")

    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Quick Add Transaction", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = type == "EXPENSE",
                onClick = { type = "EXPENSE" },
                label = { Text("Expense") }
            )
            FilterChip(
                selected = type == "INCOME",
                onClick = { type = "INCOME" },
                label = { Text("Income") }
            )
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount (₹)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Category", color = SlateGray, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.take(4).forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text(cat, fontSize = 12.sp) }
                )
            }
        }

        Text("Payment Mode", color = SlateGray, fontSize = 13.sp)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            modes.forEach { mode ->
                FilterChip(
                    selected = paymentMode == mode,
                    onClick = { paymentMode = mode },
                    label = { Text(mode, fontSize = 12.sp) }
                )
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
                    onSave(amt, type, category, paymentMode, note)
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
