package com.fintech.moneymanager.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        NotificationHelper.createChannels(applicationContext)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, "budget_and_daily_alerts")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Daily Spending Wrap-Up")
            .setContentText("Check today's auto-detected bank transactions and log any cash expenses!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(8888, notification)
        return Result.success()
    }
}
