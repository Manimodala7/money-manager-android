package com.fintech.moneymanager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fintech.moneymanager.MainActivity

object NotificationHelper {
    private const val CHANNEL_SMS_ID = "bank_sms_auto_detect"
    private const val CHANNEL_BUDGET_ID = "budget_and_daily_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val smsChannel = NotificationChannel(
                CHANNEL_SMS_ID,
                "Auto-Detected Bank Transactions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts when a bank SMS transaction is detected"
            }

            val budgetChannel = NotificationChannel(
                CHANNEL_BUDGET_ID,
                "Budget & Daily Usage Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily spending wrap-up and monthly budget warnings"
            }

            manager.createNotificationChannel(smsChannel)
            manager.createNotificationChannel(budgetChannel)
        }
    }

    fun sendSmsDetectedNotification(
        context: Context,
        amount: Double,
        type: String,
        merchant: String,
        bank: String
    ) {
        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionWord = if (type == "EXPENSE") "Debit" else "Credit"
        val notification = NotificationCompat.Builder(context, CHANNEL_SMS_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Auto-detected Transaction: ₹${amount.toInt()}")
            .setContentText("$actionWord of ₹$amount at $merchant ($bank). Tap to categorize!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun sendBudgetWarning(context: Context, category: String, spent: Double, limit: Double) {
        createChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val pct = ((spent / limit) * 100).toInt()

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Budget Alert: $category ($pct% used)")
            .setContentText("You have spent ₹${spent.toInt()} out of your ₹${limit.toInt()} monthly budget.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(category.hashCode(), notification)
    }
}
