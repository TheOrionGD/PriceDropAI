package com.example.pricedropai

import com.example.pricedropai.core.model.Store
import com.example.pricedropai.core.parser.UrlProductParser
import org.junit.Assert.*
import org.junit.Test

class UrlProductParserTest {

    @Test
    fun testAmazonUrlDetectionAndAsinExtraction() {
        val rawUrl = "https://www.amazon.in/Apple-iPhone-15-128-GB/dp/B0CHX1W1XY/ref=sr_1_1?crid=123&keywords=iphone&qid=1700000000&sprefix=iphone%2Caps%2C200&sr=8-1"
        val parsed = UrlProductParser.parse(rawUrl)

        assertEquals(Store.AMAZON, parsed.store)
        assertEquals("B0CHX1W1XY", parsed.productId)
        assertTrue(parsed.searchQuery.contains("Apple", ignoreCase = true) || parsed.searchQuery.contains("iPhone", ignoreCase = true))
    }

    @Test
    fun testTrackingParameterRemoval() {
        val dirtyUrl = "https://www.amazon.in/product-name/dp/B123456789?utm_source=google&utm_medium=cpc&utm_campaign=sale&fbclid=abc123xyz&ref=sr_1_1"
        val normalized = UrlProductParser.normalizeUrl(dirtyUrl)

        assertFalse(normalized.contains("utm_source"))
        assertFalse(normalized.contains("utm_medium"))
        assertFalse(normalized.contains("utm_campaign"))
        assertFalse(normalized.contains("fbclid"))
        assertFalse(normalized.contains("ref="))
        assertTrue(normalized.contains("amazon.in/product-name/dp/B123456789"))
    }

    @Test
    fun testFlipkartUrlDetectionAndPidExtraction() {
        val flipkartUrl = "https://www.flipkart.com/apple-iphone-15-black-128-gb/p/itm6ac6485515ae4?pid=MOBGTAGPTB3VS24W&lid=LSTMOBGTAGPTB3VS24WVUQ0OG&marketplace=FLIPKART"
        val parsed = UrlProductParser.parse(flipkartUrl)

        assertEquals(Store.FLIPKART, parsed.store)
        assertEquals("MOBGTAGPTB3VS24W", parsed.productId)
        assertTrue(parsed.searchQuery.contains("Apple", ignoreCase = true) || parsed.searchQuery.contains("iPhone", ignoreCase = true))
    }

    @Test
    fun testMyntraUrlDetection() {
        val myntraUrl = "https://www.myntra.com/shoes/nike/nike-men-air-max-running-shoes/21434522/buy"
        val parsed = UrlProductParser.parse(myntraUrl)

        assertEquals(Store.MYNTRA, parsed.store)
        assertEquals("21434522", parsed.productId)
    }

    @Test
    fun testPlainQueryExtraction() {
        val plainQuery = "Sony WH-1000XM4 Noise Cancelling Headphones"
        val parsed = UrlProductParser.parse(plainQuery)

        assertNull(parsed.store)
        assertNull(parsed.productId)
        assertEquals(plainQuery, parsed.searchQuery)
    }
}
