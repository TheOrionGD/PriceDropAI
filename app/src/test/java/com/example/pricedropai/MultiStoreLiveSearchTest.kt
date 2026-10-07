package com.example.pricedropai

import com.example.pricedropai.core.model.Store
import com.example.pricedropai.data.remote.datasource.MultiStoreProductDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MultiStoreLiveSearchTest {

    private lateinit var dataSource: MultiStoreProductDataSource

    @Before
    fun setUp() {
        dataSource = MultiStoreProductDataSource()
    }

    @Test
    fun testDynamicLiveSearchMultipleDiverseQueries() = runBlocking {
        val testQueries = listOf(
            "Apple iPhone 15",
            "Nike Air Max Shoes",
            "Milton Thermosteel Water Bottle",
            "Sony WH-1000XM5 Headphones",
            "Atomic Habits Book"
        )

        for (query in testQueries) {
            val result = dataSource.searchProducts(query)
            assertTrue("Search should succeed for query: $query", result.isSuccess)

            val products = result.getOrNull()
            assertNotNull("Products list should not be null for query: $query", products)
            assertTrue("Products list should not be empty for query: $query", products!!.isNotEmpty())

            val product = products.first()
            assertNotNull("Product title must not be null for query: $query", product.title)
            assertTrue("Product title must not be blank for query: $query", product.title.isNotBlank())

            // 1. Validate Guaranteed Product Image
            assertNotNull("Product image URL must not be null for query: $query", product.imageUrl)
            assertTrue(
                "Product image URL must be a valid http/https URL for query: $query (was: ${product.imageUrl})",
                product.imageUrl!!.startsWith("http://") || product.imageUrl!!.startsWith("https://")
            )

            // 2. Validate Real Scraped Store Offers
            assertTrue("Product must contain at least 1 verified live store offer for query: $query", product.stores.isNotEmpty())
            for (offer in product.stores) {
                assertNotNull("Offer price must not be null for store ${offer.store}", offer.price)
                assertTrue("Offer price must be positive for store ${offer.store}", offer.price!! > 0.0)
                assertTrue("Offer product URL must be non-empty", offer.productUrl.isNotBlank())
            }

            // 3. Validate Lowest Offer & Price Integrity
            assertNotNull("Lowest offer must exist for query: $query", product.lowestOffer)
            val lowestPrice = product.lowestPrice
            assertNotNull("Lowest price must be valid for query: $query", lowestPrice)
            assertTrue("Lowest price must be positive for query: $query (was: $lowestPrice)", lowestPrice!! > 0.0)

            // 4. Validate Category Detection
            assertNotNull("Category must be detected for query: $query", product.category)
            assertTrue("Category must not be blank for query: $query", product.category!!.isNotBlank())
        }
    }

    @Test
    fun testDynamicImageResolutionDirect() {
        val testLiveScrapedImages = listOf(
            "https://m.media-amazon.com/images/I/71d7rfSl0wL._AC_UY327_FMwebp_QL65_.jpg",
            "https://rukminim2.flixcart.com/image/312/312/xif0q/mobile/k/l/f/-original-imagzjh7ehevpnms.jpeg",
            "https://images.meesho.com/images/products/382029312/sfeea_512.webp"
        )

        for (scrapedUrl in testLiveScrapedImages) {
            val resolvedImage = dataSource.resolveDynamicProductImage("Samsung Phone", scrapedUrl)
            assertNotNull("Resolved image must not be null for live scraped URL", resolvedImage)
            assertEquals("Resolved image should match valid scraped candidate", scrapedUrl, resolvedImage)
        }

        // Test isValidImageUrl validation
        assertTrue(dataSource.isValidImageUrl("https://m.media-amazon.com/images/I/71d7rfSl0wL.jpg"))
        assertTrue(dataSource.isValidImageUrl("https://rukminim2.flixcart.com/image/312/312/product.jpeg"))
        assertFalse(dataSource.isValidImageUrl("not_a_valid_url"))
        assertFalse(dataSource.isValidImageUrl("https://amazon.in/s?k=phone.html"))
    }

    @Test
    fun testEmptyQueryReturnsEmptyList() = runBlocking {
        val result = dataSource.searchProducts("   ")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()!!.isEmpty())
    }
}
