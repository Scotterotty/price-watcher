package com.pricewatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertLogicTest {
    private fun step(item: WatchItem, price: Double, t: Long): CheckResult =
        PriceChecker.evaluate(item, PricePoint(t, price, "Amazon"), t)

    @Test fun firstReadingIsBaselineOnly() {
        val r = step(WatchItem(name = "x", urls = listOf("u")), 60.0, 1)
        assertNull(r.alert)
        assertEquals(1, r.item.history.size)
    }

    @Test fun dropBelowLowAlerts() {
        var item = step(WatchItem(name = "x"), 60.0, 1).item
        item = step(item, 60.0, 2).also { assertNull(it.alert) }.item
        val r = step(item, 49.0, 3)
        assertEquals(AlertReason.NEW_LOW, r.alert)
    }

    @Test fun returningToLowAlertsOnceThenQuiet() {
        var item = step(WatchItem(name = "x"), 50.0, 1).item
        item = step(item, 60.0, 2).item
        val back = step(item, 50.0, 3)
        assertEquals(AlertReason.BACK_AT_LOW, back.alert)
        assertNull(step(back.item, 50.0, 4).alert)
    }

    @Test fun targetPriceAlertsOnceUntilPriceRises() {
        // Even the first reading alerts when it's already at the target.
        var item = step(WatchItem(name = "x", targetPrice = 45.0), 40.0, 1).let {
            assertEquals(AlertReason.TARGET_HIT, it.alert); it.item
        }
        assertNull(step(item, 40.0, 2).alert)
        item = step(item, 47.0, 3).item
        assertNull(item.lastAlertedPrice)
    }
}
