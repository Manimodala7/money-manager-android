package com.fintech.moneymanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val type: String, // "EXPENSE" or "INCOME"
    val category: String,
    val subCategory: String = "General",
    val paymentMode: String, // "UPI", "Credit Card", "Cash", "Bank Transfer"
    val merchantOrNote: String,
    val bankName: String = "Union Bank *0007",
    val timestamp: Long = System.currentTimeMillis(),
    val isPendingApproval: Boolean = false,
    val rawSms: String? = null
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimit: Double
)

@Entity(tableName = "account_balances")
data class AccountBalanceEntity(
    @PrimaryKey val accountName: String,
    val baseBalance: Double // Automatically adjusted when you set your Current Balance
)

// Built-in Calculator Engine for Amount Fields (supports +, -, *, /)
// Pure Kotlin Calculator Engine (No Regex needed - supports +, -, *, /)
object MathEvaluator {
    fun evaluate(expression: String): Double? {
        val clean = expression.replace(",", "").replace("₹", "").replace(" ", "")
        if (clean.isEmpty()) return null
        return try {
            val tokens = mutableListOf<String>()
            var currentNum = StringBuilder()
            for (ch in clean) {
                if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                    if (currentNum.isNotEmpty()) {
                        tokens.add(currentNum.toString())
                        currentNum.clear()
                    }
                    tokens.add(ch.toString())
                } else {
                    currentNum.append(ch)
                }
            }
            if (currentNum.isNotEmpty()) {
                tokens.add(currentNum.toString())
            }
            if (tokens.isEmpty()) return null

            val values = mutableListOf<Double>()
            val ops = mutableListOf<String>()

            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                if ((t == "+" || t == "-") && (i == 0 || tokens[i - 1] in listOf("+", "-", "*", "/"))) {
                    val nextVal = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: return null
                    values.add(if (t == "-") -nextVal else nextVal)
                    i += 2
                } else if (t in listOf("+", "-", "*", "/")) {
                    ops.add(t)
                    i++
                } else {
                    val v = t.toDoubleOrNull() ?: return null
                    if (ops.isNotEmpty() && (ops.last() == "*" || ops.last() == "/")) {
                        val op = ops.removeAt(ops.lastIndex)
                        val prev = values.removeAt(values.lastIndex)
                        values.add(if (op == "*") prev * v else if (v != 0.0) prev / v else 0.0)
                    } else {
                        values.add(v)
                    }
                    i++
                }
            }

            var result = values.firstOrNull() ?: return null
            var opIdx = 0
            for (vIdx in 1 until values.size) {
                val op = ops.getOrNull(opIdx++) ?: break
                if (op == "+") result += values[vIdx]
                else if (op == "-") result -= values[vIdx]
            }
            result
        } catch (e: Exception) {
            null
        }
    }
}

object CategoryCatalog {
    val map: Map<String, List<String>> = linkedMapOf(
        "Food & Dining" to listOf("Zomato", "Swiggy", "Dineout", "Order", "Bought", "Cafe & Tea", "Street Food", "Snacks"),
        "Groceries" to listOf("Blinkit", "Zepto", "Instamart", "BigBasket", "DMart / Store", "Milk & Dairy", "Veggies & Fruits"),
        "Shopping" to listOf("Amazon", "Flipkart", "Myntra", "Ajio", "Electronics", "Clothing", "Footwear", "Home & Kitchen"),
        "Transport & Fuel" to listOf("Uber", "Ola", "Rapido", "Namma Yatri", "Petrol / Fuel", "Metro / Bus", "FASTag / Toll", "Parking"),
        "Bills & Utilities" to listOf("Mobile Recharge", "WiFi / Broadband", "Electricity", "Water / Gas", "DTH", "Subscriptions"),
        "Housing & Rent" to listOf("House Rent", "Maintenance", "Maid / Cook", "Laundry", "Repairs & Plumbing"),
        "Entertainment" to listOf("Movies / BookMyShow", "Netflix / OTT", "Spotify / YouTube", "Gaming", "Events & Outing"),
        "Investments" to listOf("IPO", "Mutual Funds / SIP", "Stocks / Equity", "Gold / SGB", "FD / RD", "Crypto"),
        "Health & Fitness" to listOf("Pharmacy / Meds", "Doctor Visit", "Lab Tests", "Gym / Cult", "Sports"),
        "Travel & Trips" to listOf("IRCTC / Trains", "Flights", "Hotels / Stay", "RedBus / Bus", "Holiday Spend"),
        "Personal Care" to listOf("Salon / Haircut", "Grooming / Skincare", "Spa & Wellness"),
        "Education & Work" to listOf("Online Courses", "Books", "Software / AI Tools", "Stationery"),
        "Gifts & Family" to listOf("Sent to Family", "Friends Split", "Gift / Festival", "Charity / Temple"),
        "EMI & Loans" to listOf("Credit Card EMI", "Personal Loan", "Vehicle EMI", "Slice / BNPL"),
        "Income / Credits" to listOf("Salary", "Cashback", "Friend Refund", "IPO / Stock Profit", "Interest / Dividend", "Bonus"),
        "Self Transfer" to listOf("CC Bill Repayment", "Bank to Bank", "ATM Cash Withdrawal", "UPI Lite / Wallet")
    )

    val allCategories: List<String> = map.keys.toList()

    fun getSubCategories(category: String): List<String> {
        return map[category] ?: listOf("General", "Order", "Bought", "Online", "Offline")
    }

    fun autoDetectCategory(text: String, type: String): Pair<String, String> {
        if (type == "INCOME") return "Income / Credits" to "Salary"
        val lower = text.lowercase()
        return when {
            lower.contains("zomato") -> "Food & Dining" to "Zomato"
            lower.contains("swiggy") -> "Food & Dining" to "Swiggy"
            lower.contains("dineout") || lower.contains("restaurant") || lower.contains("hotel") -> "Food & Dining" to "Dineout"
            lower.contains("blinkit") -> "Groceries" to "Blinkit"
            lower.contains("zepto") -> "Groceries" to "Zepto"
            lower.contains("instamart") || lower.contains("bigbasket") -> "Groceries" to "Instamart"
            lower.contains("amazon") -> "Shopping" to "Amazon"
            lower.contains("flipkart") -> "Shopping" to "Flipkart"
            lower.contains("myntra") || lower.contains("ajio") -> "Shopping" to "Myntra"
            lower.contains("uber") -> "Transport & Fuel" to "Uber"
            lower.contains("ola") -> "Transport & Fuel" to "Ola"
            lower.contains("rapido") -> "Transport & Fuel" to "Rapido"
            lower.contains("petrol") || lower.contains("shell") || lower.contains("hpcl") || lower.contains("bpcl") || lower.contains("iocl") -> "Transport & Fuel" to "Petrol / Fuel"
            lower.contains("jio") || lower.contains("airtel") || lower.contains("vi ") || lower.contains("bsnl") -> "Bills & Utilities" to "Mobile Recharge"
            lower.contains("bescom") || lower.contains("electricity") -> "Bills & Utilities" to "Electricity"
            lower.contains("zerodha") || lower.contains("groww") || lower.contains("upstox") || lower.contains("ipo") -> "Investments" to "IPO"
            lower.contains("irctc") -> "Travel & Trips" to "IRCTC / Trains"
            lower.contains("netflix") || lower.contains("prime") || lower.contains("hotstar") -> "Entertainment" to "Netflix / OTT"
            lower.contains("apollo") || lower.contains("pharmeasy") || lower.contains("medplus") -> "Health & Fitness" to "Pharmacy / Meds"
            else -> "Food & Dining" to "Bought"
        }
    }
}
