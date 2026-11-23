package com.pricewatch.app

/** Cleans up product links. Pure Kotlin so it can be unit tested. */
object Links {

    private val urlRx = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val asinInUrl = Regex("""/(?:dp|gp/product|gp/aw/d|exec/obidos/ASIN)/([A-Z0-9]{10})(?:[/?#]|$)""", RegexOption.IGNORE_CASE)
    private val asinInHtml = listOf(
        Regex("""<link[^>]+rel="canonical"[^>]+href="[^"]*/dp/([A-Z0-9]{10})"""),
        Regex("""name="ASIN"[^>]*value="([A-Z0-9]{10})""""),
    )

    /**
     * Pulls every link out of pasted text. Handles share text like
     * "Check this out on Amazon: https://a.co/d/abc123, ..." by dropping
     * the words around it and any trailing punctuation.
     */
    fun extract(text: String): List<String> =
        urlRx.findAll(text).map { clean(it.value) }.filter { it.length > 10 }.distinct().toList()

    fun clean(url: String): String = url.trim().trimEnd(',', '.', ';', ':', ')', ']', '!', '?', '\'', '"')

    /** Amazon product ID from a full Amazon URL, if it has one. */
    fun asinFromUrl(url: String): String? = asinInUrl.find(url)?.groupValues?.get(1)?.uppercase()

    fun asinFromHtml(html: String): String? =
        asinInHtml.firstNotNullOfOrNull { it.find(html)?.groupValues?.get(1) }

    /** Short, stable product link with no tracking parameters. */
    fun amazonProductUrl(asin: String, host: String = "amazon.com"): String {
        val h = if (host.startsWith("amazon.")) host else "amazon.com"
        return "https://www.$h/dp/$asin"
    }
}
