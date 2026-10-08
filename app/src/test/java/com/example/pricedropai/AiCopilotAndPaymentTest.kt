package com.example.pricedropai

import com.example.pricedropai.core.model.*
import com.example.pricedropai.data.remote.datasource.AiCopilotDataSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiCopilotAndPaymentTest {

    private val testProduct = Product(
        id = "iphone-15",
        title = "Apple iPhone 15 128GB",
        stores = listOf(
            StoreOffer(
                id = "offer-amazon",
                productId = "iphone-15",
                store = Store.AMAZON,
                productUrl = "https://amazon.in",
                price = 62999.0,
                originalPrice = 69999.0,
                discountPercentage = 10.0,
                availability = Availability.IN_STOCK
            ),
            StoreOffer(
                id = "offer-flipkart",
                productId = "iphone-15",
                store = Store.FLIPKART,
                productUrl = "https://flipkart.com",
                price = 59999.0,
                originalPrice = 69999.0,
                discountPercentage = 14.2,
                availability = Availability.IN_STOCK
            )
        ),
        reviewSummary = ReviewSummary(
            sentimentScore = 92,
            pros = listOf("Excellent dynamic island", "Great battery life"),
            cons = listOf("60Hz display only"),
            totalAnalyzed = 1450,
            isAvailable = true
        )
    )

    private val priceSnapshots = listOf(
        PriceSnapshot(id = 1, productId = "iphone-15", store = Store.FLIPKART, price = 64999.0, timestamp = System.currentTimeMillis() - 86400000 * 5),
        PriceSnapshot(id = 2, productId = "iphone-15", store = Store.FLIPKART, price = 59999.0, timestamp = System.currentTimeMillis())
    )

    @Test
    fun testAiCopilotCheapestStoreQuery() = runBlocking {
        val dataSource = AiCopilotDataSource()
        val response = dataSource.generateResponse(
            userPrompt = "Which store is cheapest?",
            currentProduct = testProduct,
            priceHistory = priceSnapshots
        )

        assertTrue(response.replyText.contains("Flipkart"))
        assertTrue(response.replyText.contains("59,999"))
    }

    @Test
    fun testAiCopilotPriceTrendQuery() = runBlocking {
        val dataSource = AiCopilotDataSource()
        val response = dataSource.generateResponse(
            userPrompt = "Show price trend history",
            currentProduct = testProduct,
            priceHistory = priceSnapshots
        )

        assertTrue(response.replyText.contains("decreased by ₹5,000") || response.replyText.contains("59,999"))
    }

    @Test
    fun testAiCopilotReviewSummaryQuery() = runBlocking {
        val dataSource = AiCopilotDataSource()
        val response = dataSource.generateResponse(
            userPrompt = "Summarize customer reviews and pros cons",
            currentProduct = testProduct,
            priceHistory = priceSnapshots
        )

        assertTrue(response.replyText.isNotBlank())
        assertTrue(response.replyText.contains("iPhone 15") || response.replyText.contains("price"))
    }

    @Test
    fun testPaymentOfferDiscountCalculations() {
        val basePrice = 60000.0

        val fixedOffer = PaymentOffer(
            id = "hdfc-1",
            provider = "HDFC Bank Credit Card",
            type = PaymentOfferType.INSTANT_DISCOUNT,
            description = "₹3,000 instant discount",
            amount = 3000.0
        )

        val percentOfferWithCap = PaymentOffer(
            id = "icici-1",
            provider = "ICICI Bank Credit Card",
            type = PaymentOfferType.INSTANT_DISCOUNT,
            description = "10% off up to ₹4,000",
            percentage = 10.0,
            maximumDiscount = 4000.0
        )

        // 1. Fixed discount
        val fixedDiscount = fixedOffer.amount ?: 0.0
        assertEquals(3000.0, fixedDiscount, 0.01)
        assertEquals(57000.0, basePrice - fixedDiscount, 0.01)

        // 2. Percentage with Cap (10% of 60000 = 6000, capped at 4000)
        val calculated = basePrice * (percentOfferWithCap.percentage!! / 100.0)
        val cappedDiscount = minOf(calculated, percentOfferWithCap.maximumDiscount!!)
        assertEquals(4000.0, cappedDiscount, 0.01)
        assertEquals(56000.0, basePrice - cappedDiscount, 0.01)
    }
}
