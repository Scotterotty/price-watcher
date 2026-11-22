package com.pricewatch.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: one row per item with current price and all-time low. */
class PriceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) PriceCheckWorker.runNow(context)
    }

    companion object {
        private const val ACTION_REFRESH = "com.pricewatch.app.REFRESH"
        private const val MAX_ROWS = 6

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PriceWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val items = ItemStore(context).load()
            val views = RemoteViews(context.packageName, R.layout.widget)
            views.removeAllViews(R.id.widget_rows)

            if (items.isEmpty()) {
                views.addView(R.id.widget_rows, row(context, "Tap to add an item", "", "", false, emptyList()))
            }
            items.take(MAX_ROWS).forEach { item ->
                val cur = item.current
                val low = item.lowest
                val price = cur?.let { Format.money(it.price) } ?: "—"
                val detail = when {
                    item.urls.isEmpty() -> "Add a store link"
                    cur == null -> item.lastError ?: "Not checked yet"
                    item.isAtLowest -> "Lowest price · ${cur.store}"
                    low != null -> "Low ${Format.money(low.price)} · ${cur.store}"
                    else -> cur.store
                }
                views.addView(R.id.widget_rows, row(context, item.name, price, detail, item.isAtLowest, item.history.map { it.price }))
            }

            val lastChecked = items.mapNotNull { it.lastChecked }.maxOrNull()
            views.setTextViewText(R.id.widget_updated, "Updated ${Format.ago(lastChecked)}")

            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val refresh = PendingIntent.getBroadcast(
                context, 1, Intent(context, PriceWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refresh)

            manager.updateAppWidget(ids, views)
        }

        private fun row(
            context: Context, name: String, price: String, detail: String, atLow: Boolean, prices: List<Double>,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_row).apply {
            setTextViewText(R.id.row_name, name)
            setTextViewText(R.id.row_price, price)
            setTextViewText(R.id.row_detail, detail)
            setTextColor(R.id.row_price, if (atLow) Theme.GREEN else Theme.TEXT)
            setTextColor(R.id.row_detail, if (atLow) Theme.GREEN else Theme.MUTED)
            setInt(R.id.row_root, "setBackgroundResource",
                if (atLow) R.drawable.widget_row_bg_low else R.drawable.widget_row_bg)
            if (prices.size >= 2) {
                val d = context.resources.displayMetrics.density
                setImageViewBitmap(R.id.row_spark, Sparkline.render(prices, (56 * d).toInt(), (24 * d).toInt(), atLow))
            }
        }
    }
}
