package com.fintech.moneymanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.fintech.moneymanager.MainActivity
import com.fintech.moneymanager.R

class MoneyManagerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MoneyManagerWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = context.getSharedPreferences("money_manager_balances", Context.MODE_PRIVATE)
            val lastLiveBalance = prefs.getFloat("widget_live_balance", 0f)

            val views = RemoteViews(context.packageName, R.layout.widget_money_manager)
            views.setTextViewText(
                R.id.widget_balance_text,
                "Net: ₹${String.format("%,.2f", lastLiveBalance)}"
            )

            views.setOnClickPendingIntent(
                R.id.widget_btn_add,
                buildQuickAddIntent(context, "Food & Dining", 101)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_food,
                buildQuickAddIntent(context, "Food & Dining", 102)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_transport,
                buildQuickAddIntent(context, "Transport & Fuel", 103)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_shopping,
                buildQuickAddIntent(context, "Shopping", 104)
            )
            views.setOnClickPendingIntent(
                R.id.widget_root,
                buildQuickAddIntent(context, "", 105)
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun buildQuickAddIntent(context: Context, category: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("WIDGET_QUICK_ADD", true)
                if (category.isNotEmpty()) {
                    putExtra("WIDGET_CATEGORY", category)
                }
            }
            return PendingIntent.getActivity(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
