package com.fintech.moneymanager.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fintech.moneymanager.data.AppDatabase
import com.fintech.moneymanager.data.TransactionEntity
import com.fintech.moneymanager.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {

    companion object {
        // Stores recent message hashes with timestamps to prevent duplicate pop-ups
        private val recentMessages = mutableMapOf<String, Long>()
        private const val DEDUPE_WINDOW_MS = 15_000L // 15 seconds
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        // 1. Ignore notifications from our own app
        if (packageName == applicationContext.packageName) return

        val notification = sbn.notification ?: return

        // 2. Ignore Android's duplicate "Group Summary" notifications
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Bank"
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val parsed = BankSmsParser.parse(title, text) ?: return

        // 3. Deduplicate: If this exact amount + text was processed in the last 15 seconds, skip it!
        val dedupeKey = "${parsed.bankName}_${parsed.amount}_${text.trim()}"
        val now = System.currentTimeMillis()
        synchronized(recentMessages) {
            // Clean up entries older than 15 seconds
            recentMessages.entries.removeIf { now - it.value > DEDUPE_WINDOW_MS }

            if (recentMessages.containsKey(dedupeKey)) {
                return
            }
            recentMessages[dedupeKey] = now
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.appDao().insertTransaction(
                TransactionEntity(
                    amount = parsed.amount,
                    type = parsed.type,
                    category = if (parsed.type == "INCOME") "Income / Credits" else "Food & Dining",
                    paymentMode = parsed.paymentMode,
                    merchantOrNote = parsed.merchant,
                    bankName = parsed.bankName,
                    isPendingApproval = true,
                    rawSms = text
                )
            )
            NotificationHelper.sendSmsDetectedNotification(
                context = applicationContext,
                amount = parsed.amount,
                type = parsed.type,
                merchant = parsed.merchant,
                bank = parsed.bankName
            )
        }
    }
}
