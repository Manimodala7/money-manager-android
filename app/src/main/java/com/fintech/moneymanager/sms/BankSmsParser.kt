package com.fintech.moneymanager.sms

data class ParsedSmsTransaction(
    val amount: Double,
    val type: String, // "EXPENSE" or "INCOME"
    val bankName: String,
    val merchant: String,
    val paymentMode: String
)

object BankSmsParser {

    // Regex patterns covering major Indian banks (HDFC, SBI, ICICI, Axis, Kotak, Paytm, etc.)
    // Easily extendable with custom user SMS templates!
    private val amountRegex = Regex(
        pattern = "(?:Rs\.?|INR|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)",
        option = RegexOption.IGNORE_CASE
    )

    private val debitKeywords = listOf("debited", "spent", "paid", "sent", "withdrawn", "dr", "purchase")
    private val creditKeywords = listOf("credited", "received", "deposited", "added", "cr", "refund")

    private val merchantRegex = Regex(
        pattern = "(?:at|to|vpa|info:?|towards|from)\s+([A-Za-z0-9@._\-\s]{2,25}?)(?:\s+on|\s+ref|\s+upi|\.|,|$)",
        option = RegexOption.IGNORE_CASE
    )

    fun parse(sender: String, body: String): ParsedSmsTransaction? {
        val lowerBody = body.lowercase()

        // Ignore OTPs or promotional messages
        if (lowerBody.contains("otp") || lowerBody.contains("one time password") || lowerBody.contains("offer")) {
            return null
        }

        val isDebit = debitKeywords.any { lowerBody.contains(it) }
        val isCredit = creditKeywords.any { lowerBody.contains(it) }

        if (!isDebit && !isCredit) return null

        val amountMatch = amountRegex.find(body) ?: return null
        val rawAmount = amountMatch.groupValues[1].replace(",", "")
        val amount = rawAmount.toDoubleOrNull() ?: return null

        val type = if (isDebit) "EXPENSE" else "INCOME"

        val paymentMode = when {
            lowerBody.contains("upi") || lowerBody.contains("vpa") -> "UPI"
            lowerBody.contains("card") || lowerBody.contains("pos") -> "Card"
            lowerBody.contains("atm") -> "Cash"
            else -> "Bank Transfer"
        }

        val merchantMatch = merchantRegex.find(body)
        val merchant = merchantMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Bank Transaction"

        val cleanBankName = extractBankName(sender, body)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            bankName = cleanBankName,
            merchant = merchant,
            paymentMode = paymentMode
        )
    }

    private fun extractBankName(sender: String, body: String): String {
        val s = sender.uppercase()
        return when {
            s.contains("HDFC") || body.contains("HDFC", true) -> "HDFC Bank"
            s.contains("SBI") || body.contains("SBI", true) -> "SBI"
            s.contains("ICICI") || body.contains("ICICI", true) -> "ICICI Bank"
            s.contains("AXIS") || body.contains("Axis", true) -> "Axis Bank"
            s.contains("KOTAK") || body.contains("Kotak", true) -> "Kotak Bank"
            s.contains("PNB") -> "PNB"
            s.contains("BOB") -> "Bank of Baroda"
            else -> sender.takeLast(6)
        }
    }
}
