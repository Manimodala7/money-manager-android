package com.fintech.moneymanager.sms

data class ParsedSmsTransaction(
    val amount: Double,
    val type: String, // "EXPENSE" or "INCOME"
    val bankName: String, // Maps to your 4 accounts
    val merchant: String,
    val paymentMode: String
)

object BankSmsParser {

    // Matches Rs:8000.00, Rs5926.00, Rs:335.00, Rs 9445.00, Rs. 3,015.85
    private val amountRegex = Regex(
        pattern = """(?:Rs\.?:?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""",
        option = RegexOption.IGNORE_CASE
    )

    // Word-boundary debit/credit detection for your exact SMS formats
    private val debitRegex = Regex(
        pattern = """\b(debited|dr|spent|sent|paid|withdrawn)\b""",
        option = RegexOption.IGNORE_CASE
    )
    private val creditRegex = Regex(
        pattern = """\b(credited|cr|received|deposited|refund)\b""",
        option = RegexOption.IGNORE_CASE
    )

    private val merchantRegex = Regex(
        pattern = """(?:at|to|from|towards|vpa)\s+([A-Za-z0-9@._\-\s]{2,25}?)(?:\s+on|\s+ref|\s+upi|\.|,|$)""",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): ParsedSmsTransaction? {
        val lowerBody = body.lowercase()

        if (lowerBody.contains("otp") || lowerBody.contains("one time password")) {
            return null
        }

        val isDebit = debitRegex.containsMatchIn(body)
        val isCredit = creditRegex.containsMatchIn(body)

        if (!isDebit && !isCredit) return null

        val amountMatch = amountRegex.find(body) ?: return null
        val rawAmount = amountMatch.groupValues[1].replace(",", "")
        val amount = rawAmount.toDoubleOrNull() ?: return null

        val type = if (isDebit) "EXPENSE" else "INCOME"
        val accountName = detectUserAccount(sender, body)

        val paymentMode = when {
            accountName == "Credit Card xx1009" || lowerBody.contains("credit card") -> "Credit Card"
            lowerBody.contains("upi") || lowerBody.contains("vpa") -> "UPI"
            else -> "Bank Transfer"
        }

        val merchantMatch = merchantRegex.find(body)
        val extractedMerchant = merchantMatch?.groupValues?.get(1)?.trim()
        val merchant = if (!extractedMerchant.isNullOrEmpty() && !extractedMerchant.contains("your credit card", true)) {
            extractedMerchant
        } else {
            "$accountName ${if (type == "EXPENSE") "Debit" else "Credit"}"
        }

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            bankName = accountName,
            merchant = merchant,
            paymentMode = paymentMode
        )
    }

    private fun detectUserAccount(sender: String, body: String): String {
        val combined = "$sender $body".lowercase()
        return when {
            combined.contains("0007") || combined.contains("union bank") -> "Union Bank *0007"
            combined.contains("xx002") || combined.contains("002") || combined.contains("icici") -> "ICICI Bank XX002"
            combined.contains("9448") || combined.contains("slice") -> "Slice xx9448"
            combined.contains("1009") || combined.contains("credit card") -> "Credit Card xx1009"
            else -> "Union Bank *0007"
        }
    }
}
