package com.pricewatch.app

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/** Why an alert fired, if one did. */
enum class AlertReason { NEW_LOW, BACK_AT_LOW, TARGET_HIT }

data class CheckResult(val item: WatchItem, val alert: AlertReason?)

class PriceChecker(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Checks every item, saves results, sends notifications and refreshes the widget. */
    fun checkAll() {
        val store = ItemStore(context)
        val updated = store.load().map { item ->
            val result = check(item)
            result.alert?.let { Notifier.notifyPrice(context, result.item, it) }
            result.item
        }
        // Merge by id so edits made in the app during the check aren't lost.
        val latest = store.load().associateBy { it.id }
        store.save(latest.values.map { cur ->
            val checked = updated.firstOrNull { it.id == cur.id }
            if (checked == null) cur
            else cur.copy(
                history = checked.history,
                lastChecked = checked.lastChecked,
                lastError = checked.lastError,
                lastAlertedPrice = checked.lastAlertedPrice,
            )
        })
        PriceWidget.updateAll(context)
    }

    fun check(item: WatchItem): CheckResult {
        if (item.urls.isEmpty()) {
            return CheckResult(item.copy(lastError = "Add a store link to start tracking"), null)
        }
        val now = System.currentTimeMillis()
        val outcomes = item.urls.map { fetch(it) }
        val readings = outcomes.mapNotNull { o -> o.price?.let { PricePoint(now, it, storeName(o.host)) } }
        val best = readings.minByOrNull { it.price }
            ?: return CheckResult(
                item.copy(
                    lastChecked = now,
                    lastError = outcomes.mapNotNull { it.problem }.distinct().joinToString("\n")
                        .ifEmpty { "Couldn't read a price from any link" },
                ),
                null,
            )
        return evaluate(item, best, now)
    }

    companion object {
        /** Decide whether this reading deserves an alert, and record it. */
        fun evaluate(item: WatchItem, reading: PricePoint, now: Long): CheckResult {
            val previousLow = item.lowest?.price
            val previousPrice = item.current?.price
            val history = (item.history + reading).takeLast(ItemStore.MAX_HISTORY)
            val alreadyAlerted = item.lastAlertedPrice != null && reading.price >= item.lastAlertedPrice

            val reason = when {
                // First reading is the baseline: nothing to compare against yet.
                previousLow != null && reading.price < previousLow -> AlertReason.NEW_LOW
                // Climbed back up, then returned to the all-time low.
                previousLow != null && previousPrice != null &&
                    reading.price <= previousLow && previousPrice > previousLow -> AlertReason.BACK_AT_LOW
                item.targetPrice != null && reading.price <= item.targetPrice && !alreadyAlerted ->
                    AlertReason.TARGET_HIT
                else -> null
            }
            // Once the price climbs back above what we alerted on, allow a fresh alert later.
            val alertedPrice = when {
                reason != null -> reading.price
                item.lastAlertedPrice != null && reading.price > item.lastAlertedPrice -> null
                else -> item.lastAlertedPrice
            }
            return CheckResult(
                item.copy(history = history, lastChecked = now, lastError = null, lastAlertedPrice = alertedPrice),
                reason,
            )
        }

        fun hostOf(url: String): String =
            try { URI(url.trim()).host?.removePrefix("www.") ?: "" } catch (e: Exception) { "" }

        fun storeName(host: String): String = when {
            "amazon." in host || host == "a.co" || "amzn." in host -> "Amazon"
            "walmart." in host -> "Walmart"
            "bestbuy." in host -> "Best Buy"
            "target." in host -> "Target"
            "gamestop." in host -> "GameStop"
            "nintendo." in host -> "Nintendo"
            "costco." in host -> "Costco"
            else -> host
        }
    }

    private data class Outcome(val price: Double?, val host: String, val problem: String?)

    /**
     * Try a plain HTTP request first (fast, cheap). If that's blocked or the
     * price isn't in the raw HTML, load the page in a real browser engine.
     */
    private fun fetch(url: String): Outcome {
        var host = hostOf(url)
        var lastHtml: String? = null

        httpGet(url)?.let { (html, finalHost) ->
            host = finalHost
            lastHtml = html
            PriceParser.extract(html, host)?.let { return Outcome(it, host, null) }
        }

        val page = try { WebViewFetcher.load(context, url) } catch (e: Exception) { null }
        if (page != null) {
            host = hostOf(page.finalUrl).ifEmpty { host }
            lastHtml = page.html
            PriceParser.extract(page.html, host)?.let { return Outcome(it, host, null) }
        }

        val store = storeName(host)
        val problem = when {
            lastHtml == null -> "$store: couldn't load the page"
            PriceParser.looksBlocked(lastHtml!!) -> "$store showed a robot check, will retry next time"
            else -> "$store: no price on the page (out of stock?)"
        }
        return Outcome(null, host, problem)
    }

    /** Returns the page HTML and the host it ended up on after redirects. */
    private fun httpGet(url: String): Pair<String, String>? = try {
        val req = Request.Builder()
            .url(url.trim())
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string()
            if (body == null) null else body to resp.request.url.host.removePrefix("www.")
        }
    } catch (e: Exception) {
        null
    }
}
