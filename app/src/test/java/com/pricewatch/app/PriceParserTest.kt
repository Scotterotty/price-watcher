package com.pricewatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceParserTest {

    @Test fun amazonBuyBox() {
        val html = """
            <span class="a-offscreen">$9.99</span> <!-- unrelated accessory -->
            <div id="corePrice_feature_div" class="x"><span class="a-price">
            <span class="a-offscreen">$49.99</span><span aria-hidden="true">$49.99</span></span></div>
        """
        assertEquals(49.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun amazonPriceAmountJson() {
        val html = """{"desktop_buybox_group_1":[{"displayPrice":"$129.99","priceAmount":129.99}]}"""
        assertEquals(129.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun walmartNextData() {
        val html = """<script id="__NEXT_DATA__">{"priceInfo":{"currentPrice":{"price":59.88,"priceString":"$59.88"}}}</script>"""
        assertEquals(59.88, PriceParser.extract(html, "walmart.com")!!, 0.001)
    }

    @Test fun bestBuy() {
        val html = """{"skuId":"123","customerPrice":69.99,"regularPrice":79.99}"""
        assertEquals(69.99, PriceParser.extract(html, "bestbuy.com")!!, 0.001)
    }

    @Test fun jsonLdOffer() {
        val html = """
            <script type="application/ld+json">{"@type":"Organization","name":"Shop"}</script>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Game",
             "offers":{"@type":"Offer","priceCurrency":"USD","price":"1,049.00"}}
            </script>
        """
        assertEquals(1049.0, PriceParser.extract(html, "shop.example")!!, 0.001)
    }

    @Test fun metaTag() {
        val html = """<meta property="product:price:amount" content="39.95">"""
        assertEquals(39.95, PriceParser.extract(html, "gamestop.com")!!, 0.001)
    }

    @Test fun amazonMobilePriceToPay() {
        val html = """<div id="apex_offerDisplay_mobile"><span class="a-price aok-align-center priceToPay" data-a-size="xl">
            <span class="a-offscreen">$64.99</span><span aria-hidden="true">...</span></span></div>"""
        assertEquals(64.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun targetRendered() {
        val html = """<span data-test="product-price" class="styles__x">$69.99</span>"""
        assertEquals(69.99, PriceParser.extract(html, "target.com")!!, 0.001)
    }

    @Test fun detectsRobotCheck() {
        assertTrue(PriceParser.looksBlocked("<form action=\"/errors/validateCaptcha\">Type the characters</form>"))
    }

    @Test fun amazonVariantHiddenInput() {
        val html = """<div id="twister_feature_div"><input type="hidden" id="twister-plus-price-data-price" value="289.99" />
            <span class="a-price"><span class="a-offscreen"> </span></span></div>"""
        assertEquals(289.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun amazonAokOffscreenWithAttributes() {
        val html = """<div id="corePriceDisplay_mobile_feature_div"><span class="a-price" data-a-size="xl">
            <span class="aok-offscreen" aria-hidden="false">  $289.99  </span></span></div>"""
        assertEquals(289.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun amazonWholeAndFraction() {
        val html = """<div id="corePriceDisplay_mobile_feature_div"><span class="a-price-symbol">$</span>
            <span class="a-price-whole">1,289<span class="a-price-decimal">.</span></span><span class="a-price-fraction">99</span></div>"""
        assertEquals(1289.99, PriceParser.extract(html, "amazon.com")!!, 0.001)
    }

    @Test fun newegg() {
        val html = """<li class="price-current"><span class="price-current-label"></span>$<strong>289</strong><sup>.99</sup></li>"""
        assertEquals(289.99, PriceParser.extract(html, "newegg.com")!!, 0.001)
        assertEquals(98.99, PriceParser.extract("""{"Item":{"FinalPrice":98.99}}""", "newegg.com")!!, 0.001)
    }

    @Test fun nothingFound() {
        assertNull(PriceParser.extract("<html><body>Sold out</body></html>", "target.com"))
    }
}
