package com.pricewatch.app

import android.app.Application

class PriceWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannel(this)
        PriceCheckWorker.schedule(this)
    }
}
