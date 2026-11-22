package com.pricewatch.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object Notifier {
    private const val CHANNEL = "price_alerts"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Price alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when a watched item hits its lowest price"
            }
        )
    }

    fun notifyPrice(context: Context, item: WatchItem, reason: AlertReason) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val cur = item.current ?: return
        val title = when (reason) {
            AlertReason.NEW_LOW -> "New lowest price: ${Format.money(cur.price)}"
            AlertReason.BACK_AT_LOW -> "Back at its lowest: ${Format.money(cur.price)}"
            AlertReason.TARGET_HIT -> "Hit your target: ${Format.money(cur.price)}"
        }
        // Tapping the notification opens the store that has the low price.
        val url = item.urls.firstOrNull { PriceChecker.storeName(PriceChecker.hostOf(it)) == cur.store }
            ?: item.urls.firstOrNull()
        val intent = if (url != null) Intent(Intent.ACTION_VIEW, Uri.parse(url))
        else Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, item.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_tag)
            .setContentTitle(title)
            .setContentText("${item.name} at ${cur.store}")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(item.id.hashCode(), n)
    }
}
