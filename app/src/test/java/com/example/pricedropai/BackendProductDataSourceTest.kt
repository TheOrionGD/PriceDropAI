package com.example.pricedropai

import com.example.pricedropai.data.remote.datasource.BackendProductDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BackendProductDataSourceTest {

    private lateinit var dataSource: BackendProductDataSource

    @Before
    fun setUp() {
        dataSource = BackendProductDataSource()
    }

    @Test
    fun testRenderDeployedBackendProductSearch() = runBlocking {
        val testQueries = listOf("laptop", "iphone", "headphones")

        for (query in testQueries) {
            println("Testing Render backend query: '$query'")
            val result = dataSource.searchProducts(query)
            assertTrue("Backend query '$query' should return success", result.isSuccess)

            val products = result.getOrNull()
            assertNotNull("Products list for '$query' should not be null", products)
            assertTrue("Products list for '$query' should not be empty", products!!.isNotEmpty())

            val product = products.first()
            println("Product fetched: ${product.title}")
            println("Image URL: ${product.imageUrl}")
            println("Category: ${product.category}")
            println("Stores count: ${product.stores.size}")

            // 1. Verify Title & ID
            assertTrue("Product title should be non-blank", product.title.isNotBlank())
            assertTrue("Product ID should be non-blank", product.id.isNotBlank())

            // 2. Verify Image URL
            assertNotNull("Product image URL should not be null", product.imageUrl)
            assertTrue(
                "Image URL must start with http/https: ${product.imageUrl}",
                product.imageUrl!!.startsWith("http://") || product.imageUrl!!.startsWith("https://")
            )

            // 3. Verify Stores & Prices
            assertTrue("Product must have at least 1 store offer", product.stores.isNotEmpty())
            val validPriceStore = product.stores.find { it.price != null && it.price!! > 0 }
            assertNotNull("At least one store offer should have a valid price", validPriceStore)

            // 4. Verify Lowest Price calculation
            val effectivePrice = product.lowestPrice ?: validPriceStore?.price
            assertNotNull("Valid price should be computed", effectivePrice)
            assertTrue("Price must be > 0", effectivePrice!! > 0)
        }
    }
}
