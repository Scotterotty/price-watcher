package com.pricewatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {

    @Test fun stripsTrailingCommaFromShareText() {
        val pasted = "Check out this deal on Amazon https://a.co/d/02fbWkIn, it's great"
        assertEquals(listOf("https://a.co/d/02fbWkIn"), Links.extract(pasted))
    }

    @Test fun multipleLinksOnePerLineOrNot() {
        val pasted = "https://www.bestbuy.com/site/x/123.p?skuId=123\nhttps://www.walmart.com/ip/abc/456."
        assertEquals(
            listOf("https://www.bestbuy.com/site/x/123.p?skuId=123", "https://www.walmart.com/ip/abc/456"),
            Links.extract(pasted),
        )
    }

    @Test fun asinFromFullUrl() {
        assertEquals("B0DXYZ1234", Links.asinFromUrl("https://www.amazon.com/Some-Game/dp/B0DXYZ1234/ref=sr_1_1?th=1"))
        assertEquals("B0DXYZ1234", Links.asinFromUrl("https://www.amazon.com/gp/product/B0DXYZ1234?psc=1"))
        assertNull(Links.asinFromUrl("https://a.co/d/02fbWkIn"))
    }

    @Test fun asinFromCanonicalLink() {
        val html = """<link rel="canonical" href="https://www.amazon.com/Some-Game/dp/B0DXYZ1234" />"""
        assertEquals("B0DXYZ1234", Links.asinFromHtml(html))
    }

    @Test fun productUrlKeepsCountry() {
        assertEquals("https://www.amazon.com/dp/B0DXYZ1234", Links.amazonProductUrl("B0DXYZ1234", "amazon.com"))
        assertEquals("https://www.amazon.ca/dp/B0DXYZ1234", Links.amazonProductUrl("B0DXYZ1234", "amazon.ca"))
        assertEquals("https://www.amazon.com/dp/B0DXYZ1234", Links.amazonProductUrl("B0DXYZ1234", "a.co"))
    }
}
