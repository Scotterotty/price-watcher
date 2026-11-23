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

    // Amazon's hidden screen-reader price text: <span class="a-offscreen">$289.99</span>
    // (newer layouts use "aok-offscreen" and may add attributes or spaces).
    private const val OFF = """class="(?:a|aok)-offscreen[^"]*"[^>]*>\s*\$\s*$NUM"""
    private const val BOX = """id="(?:corePrice|apex)[A-Za-z_]*""""

    private val amazon = listOf(
        // "Price to pay" block, used on both the desktop and mobile sites.
        rx("""priceToPay.{0,500}?$OFF"""),
        rx("""$BOX.{0,8000}?$OFF"""),
        // Pages with size/colour variants keep the selected variant's price in hidden inputs.
        rx("""twister-plus-price-data-price"[^>]*?value="$NUM""""),
        rx("""attach-base-product-price"[^>]*?value="$NUM""""),
        rx(""""priceAmount"\s*:\s*$NUM"""),
        // Visible price split into whole + fraction: 289<sup>.99</sup>
        rx("""$BOX.{0,8000}?class="a-price-whole">([0-9,]+).{0,160}?class="a-price-fraction">([0-9]{2})"""),
        rx("""id="priceblock_(?:ourprice|dealprice)"[^>]*>\s*\$\s*$NUM"""),
        // Last resort: the first price on the page, which is the main one on product pages.
        rx(OFF),
    )

    private val newegg = listOf(
        rx(""""FinalPrice"\s*:\s*$NUM"""),
        rx("""class="price-current"[^>]*>.{0,200}?<strong>([0-9,]+)</strong>\s*<sup>\.?([0-9]{2})</sup>"""),
    )

    private val walmart = listOf(
        rx(""""currentPrice"\s*:\s*\{[^}]*?"price"\s*:\s*$NUM"""),
        rx("""itemprop="price"[^>]*content="$NUM""""),
    )

    private val bestBuy = listOf(
        rx(""""customerPrice"\s*:\s*$NUM"""),
        rx("""priceView-customer-price.{0,300}?>\s*\$$NUM"""),
        rx(""""currentPrice"\s*:\s*$NUM"""),
    )

    private val target = listOf(
        rx(""""current_retail"\s*:\s*$NUM"""),
        rx("""data-test="product-price"[^>]*>\s*\$$NUM"""),
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
            "newegg." in h -> newegg
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

    /** True when the page is a bot/captcha check instead of the product. */
    fun looksBlocked(html: String): Boolean {
        val h = html.lowercase()
        return "captcha" in h || "robot check" in h || "are you a human" in h ||
            "press & hold" in h || "access denied" in h || "/errors/validatecaptcha" in h
    }

    private fun firstMatch(html: String, patterns: List<Regex>): Double? {
        for (p in patterns) {
            val m = p.find(html) ?: continue
            val g = m.groupValues
            // Patterns with two groups capture whole dollars and cents separately.
            val raw = if (g.size >= 3 && g[2].isNotEmpty()) "${g[1]}.${g[2]}" else g[1]
            toPrice(raw)?.let { return it }
        }
        return null
    }

    private fun toPrice(raw: String): Double? =
        raw.replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 && it < 100_000.0 }
}
