package com.pricewatch.app

import java.text.NumberFormat
import java.util.Locale

object Format {
    private val fmt = NumberFormat.getCurrencyInstance(Locale.US)
    fun money(v: Double): String = fmt.format(v)

    fun ago(time: Long?, now: Long = System.currentTimeMillis()): String {
        if (time == null) return "never"
        val mins = (now - time) / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    }
}
