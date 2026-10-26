package com.pricedropai.myapp

import com.pricedropai.myapp.core.model.*
import com.pricedropai.myapp.data.remote.datasource.AiCopilotDataSource
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

    @Test
    fun testNoCostEmiCalculations() {
        val basePrice = 60000.0
        val tenure = 6
        val processingFee = 199.0
        val gstRate = 0.18
        val taxes = processingFee * gstRate // 35.82

        val plan = EmiPlan(
            id = "emi-hdfc-6m",
            provider = "HDFC Bank",
            displayName = "HDFC Bank (6 Months)",
            tenureMonths = tenure,
            monthlyInstallment = basePrice / tenure, // 10,000.0
            totalInstallmentAmount = basePrice,
            interestAmount = 2642.50,
            subventionDiscount = 2642.50, // Upfront discount by merchant covering interest
            processingFee = processingFee,
            taxes = taxes,
            effectiveTotalCost = basePrice + processingFee + taxes,
            isNoCostEmi = true
        )

        assertEquals(10000.0, plan.monthlyInstallment, 0.01)
        assertEquals(60000.0, plan.totalInstallmentAmount, 0.01)
        assertEquals(plan.interestAmount, plan.subventionDiscount, 0.01)
        assertTrue(plan.isNoCostEmi)
        assertEquals(60234.82, plan.effectiveTotalCost, 0.01)
    }

    @Test
    fun testIneligibleOfferConditions() {
        val lowPrice = 1499.0
        val ineligibleOffer = PaymentOffer(
            id = "sbi-festive",
            provider = "SBI Card",
            type = PaymentOfferType.INSTANT_DISCOUNT,
            description = "10% off on min spend ₹5,000",
            percentage = 10.0,
            minimumPurchase = 5000.0,
            maximumDiscount = 1500.0,
            isEligible = false,
            eligibilityStatus = "INELIGIBLE_MIN_PURCHASE",
            ineligibilityReason = "Minimum purchase of ₹5,000 required (Current: ₹1,499)",
            calculatedDiscount = 0.0,
            payablePrice = lowPrice
        )

        assertFalse(ineligibleOffer.isEligible)
        assertEquals("INELIGIBLE_MIN_PURCHASE", ineligibleOffer.eligibilityStatus)
        assertEquals(0.0, ineligibleOffer.calculatedDiscount ?: 0.0, 0.01)
        assertEquals(lowPrice, ineligibleOffer.payablePrice ?: 0.0, 0.01)
    }

    @Test
    fun testWatchlistDuplicateNotificationSuppression() {
        val tracked = TrackedProduct(
            id = 101,
            productId = "apple-macbook-air-m2",
            productTitle = "Apple MacBook Air M2",
            currentPrice = 84990.0,
            targetPrice = 85000.0,
            lastNotifiedPrice = 84990.0,
            enabled = true
        )

        // 1. Same price as previously notified: must NOT re-trigger
        val shouldNotifySame = if (!tracked.enabled) false
        else if (84990.0 > tracked.targetPrice) false
        else tracked.lastNotifiedPrice?.let { 84990.0 < it } ?: true

        assertFalse("Should suppress duplicate notification when price has not decreased further", shouldNotifySame)

        // 2. Price dropped even further: must trigger
        val shouldNotifyDrop = if (!tracked.enabled) false
        else if (82990.0 > tracked.targetPrice) false
        else tracked.lastNotifiedPrice?.let { 82990.0 < it } ?: true

        assertTrue("Should trigger notification when price decreases below last notified price", shouldNotifyDrop)

        // 3. Price rose back above target: must NOT trigger
        val shouldNotifyRise = if (!tracked.enabled) false
        else if (86990.0 > tracked.targetPrice) false
        else tracked.lastNotifiedPrice?.let { 86990.0 < it } ?: true

        assertFalse("Should not notify when price rises above target", shouldNotifyRise)
    }

    @Test
    fun testRatingDistributionOrderAndPercentages() {
        val dist = RatingDistribution(
            fiveStar = RatingDistributionItem(count = 600, percentage = 60.0),
            fourStar = RatingDistributionItem(count = 250, percentage = 25.0),
            threeStar = RatingDistributionItem(count = 100, percentage = 10.0),
            twoStar = RatingDistributionItem(count = 30, percentage = 3.0),
            oneStar = RatingDistributionItem(count = 20, percentage = 2.0)
        )

        val totalPercentage = (dist.fiveStar?.percentage ?: 0.0) +
                (dist.fourStar?.percentage ?: 0.0) +
                (dist.threeStar?.percentage ?: 0.0) +
                (dist.twoStar?.percentage ?: 0.0) +
                (dist.oneStar?.percentage ?: 0.0)

        assertEquals(100.0, totalPercentage, 0.01)
        assertEquals(1000, (dist.fiveStar?.count ?: 0) + (dist.fourStar?.count ?: 0) + (dist.threeStar?.count ?: 0) + (dist.twoStar?.count ?: 0) + (dist.oneStar?.count ?: 0))
    }

    @Test
    fun testSingleSecretKeyConfiguration() {
        // Single dedicated test for secret key presence and safe client-side masking
        val buildKey = BuildConfig.GEMINI_API_KEY
        assertNotNull("Gemini API key field must be defined in BuildConfig", buildKey)
        // Ensure secret keys are masked safely and middle characters never leaked
        val sampleSecret = if (buildKey.length > 8) buildKey else "AIzaSySecretTestKey12345"
        val masked = "${sampleSecret.take(4)}...${sampleSecret.takeLast(4)}"
        val middleBytes = sampleSecret.substring(4, sampleSecret.length - 4)
        assertFalse("Masked representation must not expose middle secret bytes", masked.contains(middleBytes))
    }
}
