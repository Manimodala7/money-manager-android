package com.fintech.moneymanager.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.fintech.moneymanager.data.AppDatabase
import com.fintech.moneymanager.data.TransactionEntity
import com.fintech.moneymanager.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Bank"
        val fullBody = messages.joinToString(separator = "") { it.messageBody ?: "" }

        val parsed = BankSmsParser.parse(sender, fullBody) ?: return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val entity = TransactionEntity(
                amount = parsed.amount,
                type = parsed.type,
                category = "Uncategorized",
                paymentMode = parsed.paymentMode,
                merchantOrNote = parsed.merchant,
                bankName = parsed.bankName,
                isPendingApproval = true,
                rawSms = fullBody
            )
            db.appDao().insertTransaction(entity)

            NotificationHelper.sendSmsDetectedNotification(
                context = context,
                amount = parsed.amount,
                type = parsed.type,
                merchant = parsed.merchant,
                bank = parsed.bankName
            )
        }
    }
}
