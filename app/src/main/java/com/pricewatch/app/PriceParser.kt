package com.pricewatch.app

/**
 * Pulls the current selling price out of a retailer product page.
 *
 * Pure Kotlin (no Android classes) so it can be unit tested on the JVM.
 * Tries store-specific patterns first, then generic structured data
 * (schema.org JSON-LD and price meta tags) that most retailers include.
 */
object PriceParser {

    private const val NUM = """([0-9]{1,5}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)"""

    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val amazon = listOf(
        // The main buy-box price block, then its hidden "$59.99" text.
        rx("""id="corePrice(?:Display_desktop|_feature|_desktop)_div".{0,3000}?class="a-offscreen">\s*\$\s*$NUM"""),
        rx("""id="apex_desktop".{0,3000}?class="a-offscreen">\s*\$\s*$NUM"""),
        rx(""""priceAmount"\s*:\s*$NUM"""),
        rx("""id="priceblock_(?:ourprice|dealprice)"[^>]*>\s*\$\s*$NUM"""),
    )

    private val walmart = listOf(
        rx(""""currentPrice"\s*:\s*\{[^}]*?"price"\s*:\s*$NUM"""),
        rx("""itemprop="price"[^>]*content="$NUM""""),
    )

    private val bestBuy = listOf(
        rx(""""customerPrice"\s*:\s*$NUM"""),
        rx(""""currentPrice"\s*:\s*$NUM"""),
    )

    private val target = listOf(
        rx(""""current_retail"\s*:\s*$NUM"""),
        rx(""""formatted_current_price"\s*:\s*"\$$NUM"""),
    )

    private val generic = listOf(
        rx("""<meta[^>]+(?:property|name|itemprop)="(?:product:price:amount|og:price:amount|price)"[^>]+content="\$?$NUM""""),
        rx("""<meta[^>]+content="\$?$NUM"[^>]+(?:property|name|itemprop)="(?:product:price:amount|og:price:amount|price)""""),
        rx("""itemprop="price"[^>]*content="\$?$NUM""""),
    )

    private val ldJsonBlock = rx("""<script[^>]+type="application/ld\+json"[^>]*>(.*?)</script>""")
    private val ldPrice = rx(""""(?:price|lowPrice)"\s*:\s*"?\$?$NUM"?""")

    /** Returns the price in dollars, or null if none could be found. */
    fun extract(html: String, host: String = ""): Double? {
        val h = host.lowercase()
        val storeSpecific = when {
            "amazon." in h -> amazon
            "walmart." in h -> walmart
            "bestbuy." in h -> bestBuy
            "target." in h -> target
            else -> emptyList()
        }
        firstMatch(html, storeSpecific)?.let { return it }

        // schema.org Product/Offer data, used by most stores (and by Google Shopping).
        for (block in ldJsonBlock.findAll(html)) {
            val json = block.groupValues[1]
            if (!json.contains("Offer", ignoreCase = true)) continue
            ldPrice.find(json)?.let { m -> toPrice(m.groupValues[1])?.let { return it } }
        }

        return firstMatch(html, generic)
    }

    private fun firstMatch(html: String, patterns: List<Regex>): Double? {
        for (p in patterns) {
            val m = p.find(html) ?: continue
            toPrice(m.groupValues[1])?.let { return it }
        }
        return null
    }

    private fun toPrice(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 && it < 100_000.0 }
}
