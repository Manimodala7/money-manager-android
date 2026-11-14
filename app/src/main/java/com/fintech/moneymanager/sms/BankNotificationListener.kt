package com.fintech.moneymanager.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.fintech.moneymanager.data.AppDatabase
import com.fintech.moneymanager.data.CategoryCatalog
import com.fintech.moneymanager.data.TransactionEntity
import com.fintech.moneymanager.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Bank"
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: return

        val parsed = BankSmsParser.parse(title, text) ?: return
        val (autoCat, autoSubCat) = CategoryCatalog.autoDetectCategory(text, parsed.type)

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.appDao().insertTransaction(
                TransactionEntity(
                    amount = parsed.amount,
                    type = parsed.type,
                    category = autoCat,
                    subCategory = autoSubCat,
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
                merchant = "${parsed.merchant} ($autoSubCat)",
                bank = parsed.bankName
            )
        }
    }
}
