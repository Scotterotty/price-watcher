package com.pricewatch.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PricePoint(val time: Long, val price: Double, val store: String)

data class WatchItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val urls: List<String> = emptyList(),
    /** Optional: also alert when the price is at or below this. */
    val targetPrice: Double? = null,
    val history: List<PricePoint> = emptyList(),
    val lastChecked: Long? = null,
    val lastError: String? = null,
    /** Price we last sent a notification for, so we don't repeat ourselves. */
    val lastAlertedPrice: Double? = null,
) {
    val current: PricePoint? get() = history.lastOrNull()
    val lowest: PricePoint? get() = history.minByOrNull { it.price }
    val isAtLowest: Boolean
        get() {
            val c = current ?: return false
            val l = lowest ?: return false
            return history.size > 1 && c.price <= l.price
        }
}

/** Saves the watch list as JSON in SharedPreferences. */
class ItemStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("items", Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<WatchItem> {
        val raw = prefs.getString(KEY, null) ?: return seed()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(items: List<WatchItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun upsert(item: WatchItem) {
        val items = load().toMutableList()
        val i = items.indexOfFirst { it.id == item.id }
        if (i >= 0) items[i] = item else items.add(item)
        save(items)
    }

    @Synchronized
    fun delete(id: String) = save(load().filterNot { it.id == id })

    /** First launch: start with the items Scott asked to watch, links to be added in the app. */
    private fun seed(): List<WatchItem> {
        val items = listOf(
            WatchItem(name = "Final Fantasy: Resonance (Switch, physical)"),
            WatchItem(name = "Nintendo Switch 2 microSD Express 1 TB"),
        )
        save(items)
        return items
    }

    private fun toJson(it: WatchItem) = JSONObject().apply {
        put("id", it.id)
        put("name", it.name)
        put("urls", JSONArray(it.urls))
        it.targetPrice?.let { t -> put("targetPrice", t) }
        it.lastChecked?.let { t -> put("lastChecked", t) }
        it.lastError?.let { e -> put("lastError", e) }
        it.lastAlertedPrice?.let { p -> put("lastAlertedPrice", p) }
        put("history", JSONArray().apply {
            it.history.forEach { p ->
                put(JSONObject().put("t", p.time).put("p", p.price).put("s", p.store))
            }
        })
    }

    private fun fromJson(o: JSONObject): WatchItem {
        val urls = o.optJSONArray("urls") ?: JSONArray()
        val hist = o.optJSONArray("history") ?: JSONArray()
        return WatchItem(
            id = o.getString("id"),
            name = o.getString("name"),
            urls = (0 until urls.length()).map { urls.getString(it) },
            targetPrice = if (o.has("targetPrice")) o.getDouble("targetPrice") else null,
            history = (0 until hist.length()).map {
                val p = hist.getJSONObject(it)
                PricePoint(p.getLong("t"), p.getDouble("p"), p.optString("s"))
            },
            lastChecked = if (o.has("lastChecked")) o.getLong("lastChecked") else null,
            lastError = if (o.has("lastError")) o.getString("lastError") else null,
            lastAlertedPrice = if (o.has("lastAlertedPrice")) o.getDouble("lastAlertedPrice") else null,
        )
    }

    companion object {
        private const val KEY = "items_v1"
        const val MAX_HISTORY = 1000
    }
}
