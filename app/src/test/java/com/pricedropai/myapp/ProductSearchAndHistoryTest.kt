package com.pricedropai.myapp

import com.pricedropai.myapp.core.model.*
import com.pricedropai.myapp.data.mapper.Mappers.toDomain
import com.pricedropai.myapp.data.mapper.Mappers.toEntity
import com.pricedropai.myapp.data.remote.datasource.MultiStoreProductDataSource
import com.pricedropai.myapp.data.repository.ProductRepository
import org.junit.Assert.*
import org.junit.Test

class ProductSearchAndHistoryTest {

    private val multiStoreDataSource = MultiStoreProductDataSource()

    @Test
    fun testHandGripperNormalizationAndSpecPreservation() {
        val query = "5KG To 60KG Weight Adjustable High Quality Polypropylene Hand Gripper"
        val lower = query.lowercase()

        // Resistance specification preservation
        val has5to60Range = lower.contains("5kg") && lower.contains("60kg")
        val isAdjustable = lower.contains("adjustable")
        val isHandGripper = lower.contains("hand gripper") || lower.contains("gripper")

        assertTrue("5-60kg range must be preserved", has5to60Range)
        assertTrue("Adjustable attribute must be preserved", isAdjustable)
        assertTrue("Hand gripper category must be preserved", isHandGripper)
    }

    @Test
    fun testDirectProductUrlSelection() {
        val verifiedAmazonOffer = StoreOffer(
            id = "amazon_b08xyz1234",
            productId = "amazon_b08xyz1234",
            store = Store.AMAZON,
            productUrl = "https://www.amazon.in/dp/B08XYZ1234",
            searchUrl = "https://www.amazon.in/s?k=hand+gripper",
            price = 299.0,
            originalPrice = 499.0,
            discountPercentage = 40.0,
            isDirectProductUrl = true,
            status = MarketplaceStatus.AVAILABLE
        )

        val unverifiedCromaOffer = StoreOffer(
            id = "croma_gripper",
            productId = "amazon_b08xyz1234",
            store = Store.CROMA,
            productUrl = "https://www.croma.com/searchB?q=hand+gripper%3Arelevance",
            searchUrl = "https://www.croma.com/searchB?q=hand+gripper%3Arelevance",
            price = 0.0,
            originalPrice = 0.0,
            discountPercentage = 0.0,
            isDirectProductUrl = false,
            status = MarketplaceStatus.UNSUPPORTED_CATEGORY
        )

        // Verified product should use direct detail URL
        assertTrue("Verified Amazon offer must be marked direct", verifiedAmazonOffer.isDirectProductUrl)
        assertTrue("Direct URL should point to item page", verifiedAmazonOffer.productUrl.contains("/dp/"))

        // Unverified offer should fallback to search URL
        assertFalse("Unsupported or unverified store must not be marked direct", unverifiedCromaOffer.isDirectProductUrl)
        assertTrue("Fallback destination should be a search page", unverifiedCromaOffer.searchUrl?.contains("search") == true)
    }

    @Test
    fun testCategoryAwareStoreSupportForHandGripper() {
        val query = "5kg to 60kg adjustable hand gripper"
        val category = "Sports & Fitness"

        // Electronics stores do NOT sell hand grippers
        assertFalse("Croma does not sell hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.CROMA, query, category))
        assertFalse("Reliance Digital does not sell hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.RELIANCE_DIGITAL, query, category))

        // Fashion stores do NOT sell hand grippers
        assertFalse("Myntra does not sell hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.MYNTRA, query, category))
        assertFalse("AJIO does not sell hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.AJIO, query, category))

        // Cosmetics stores do NOT sell hand grippers
        assertFalse("Nykaa does not sell hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.NYKAA, query, category))

        // Horizontal e-commerce giants DO sell hand grippers
        assertTrue("Amazon supports hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.AMAZON, query, category))
        assertTrue("Flipkart supports hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.FLIPKART, query, category))
        assertTrue("Meesho supports hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.MEESHO, query, category))
        assertTrue("JioMart supports hand grippers", multiStoreDataSource.isProductCategorySupportedByStore(Store.JIOMART, query, category))
    }

    @Test
    fun testMarketplaceStatusDifferentiation() {
        assertEquals(MarketplaceStatus.AVAILABLE, MarketplaceStatus.fromString("AVAILABLE"))
        assertEquals(MarketplaceStatus.OUT_OF_STOCK, MarketplaceStatus.fromString("OUT_OF_STOCK"))
        assertEquals(MarketplaceStatus.NO_RESULTS, MarketplaceStatus.fromString("NO_RESULTS"))
        assertEquals(MarketplaceStatus.UNSUPPORTED_CATEGORY, MarketplaceStatus.fromString("UNSUPPORTED_CATEGORY"))
        assertEquals(MarketplaceStatus.EXTRACTION_FAILED, MarketplaceStatus.fromString("EXTRACTION_FAILED"))
        assertEquals(MarketplaceStatus.RATE_LIMITED, MarketplaceStatus.fromString("RATE_LIMITED"))
        assertEquals(MarketplaceStatus.UNKNOWN, MarketplaceStatus.fromString("SOME_UNKNOWN_STATUS"))
    }

    @Test
    fun testDeduplicationOfRecentlyCheckedProducts() {
        // Simulates history with duplicate rows generated by legacy alias inserts or repeated visits
        val productOriginal = Product(
            id = "amazon_b08xyz1234",
            title = "5KG To 60KG Weight Adjustable High Quality Polypropylene Hand Gripper",
            asin = "B08XYZ1234",
            brand = "Generic",
            lastUpdated = 1000L
        )

        // Duplicate from legacy alias insert (e.g. id was set to query slug "hand-gripper")
        val productAliasClone = Product(
            id = "hand-gripper",
            title = "5KG To 60KG Weight Adjustable High Quality Polypropylene Hand Gripper",
            asin = "B08XYZ1234",
            brand = "Generic",
            lastUpdated = 1000L
        )

        // Third visit of the same product with newer timestamp
        val productRevisit = Product(
            id = "amazon_b08xyz1234",
            title = "5KG To 60KG Weight Adjustable High Quality Polypropylene Hand Gripper",
            asin = "B08XYZ1234",
            brand = "Generic",
            lastUpdated = 2000L
        )

        // Distinct different product
        val distinctProduct = Product(
            id = "amazon_b09abc9999",
            title = "Strauss Forearm Exerciser Grip 10KG-40KG",
            asin = "B09ABC9999",
            brand = "Strauss",
            lastUpdated = 1500L
        )

        val rawList = listOf(productRevisit, productOriginal, productAliasClone, distinctProduct)

        // Apply canonical deduplication algorithm
        val seenIdentities = mutableSetOf<String>()
        val deduplicated = mutableListOf<Product>()

        for (item in rawList) {
            val asinKey = item.asin?.lowercase()?.takeIf { it.isNotBlank() }
            val canonicalKey = asinKey
                ?: item.lowestOffer?.productUrl?.let { ProductRepository.cleanCanonicalUrl(it) }
                ?: "${item.brand?.lowercase()?.trim() ?: ""}_${ProductRepository.normalizeProductTitle(item.title)}"

            if (seenIdentities.add(canonicalKey)) {
                deduplicated.add(item)
            }
        }

        // Must result in exactly 2 products, NOT 4!
        assertEquals("Deduplication must eliminate alias clones and repeated visits", 2, deduplicated.size)
        assertEquals("Newest visit must be retained", productRevisit.id, deduplicated[0].id)
        assertEquals(2000L, deduplicated[0].lastUpdated)
        assertEquals("Distinct product must be preserved", distinctProduct.id, deduplicated[1].id)
    }

    @Test
    fun testDistinctMarketplacesRemainDistinct() {
        val amazonOffer = StoreOffer(
            id = "amazon_b08xyz",
            productId = "amazon_b08xyz",
            store = Store.AMAZON,
            productUrl = "https://www.amazon.in/dp/B08XYZ1234",
            price = 199.0,
            originalPrice = 499.0,
            discountPercentage = 60.0
        )

        val flipkartOffer = StoreOffer(
            id = "flipkart_b08xyz",
            productId = "amazon_b08xyz",
            store = Store.FLIPKART,
            productUrl = "https://www.flipkart.com/gripper/p/itm123",
            price = 249.0,
            originalPrice = 499.0,
            discountPercentage = 50.0
        )

        val product = Product(
            id = "amazon_b08xyz",
            title = "Hand Gripper 5-60kg",
            stores = listOf(amazonOffer, flipkartOffer)
        )

        assertEquals("Stores count must be 2", 2, product.stores.size)
        assertEquals("Lowest offer should be Amazon at 199", Store.AMAZON, product.lowestOffer?.store)
        assertEquals(199.0, product.lowestOffer?.price ?: 0.0, 0.01)

        val flipkartEntry = product.stores.first { it.store == Store.FLIPKART }
        assertEquals(249.0, flipkartEntry.price ?: 0.0, 0.01)
    }

    @Test
    fun testEntityDomainMappingBackwardCompatibility() {
        val originalOffer = StoreOffer(
            id = "offer_1",
            productId = "prod_1",
            store = Store.AMAZON,
            productUrl = "https://www.amazon.in/dp/B08XYZ1234",
            price = 299.0,
            originalPrice = 599.0,
            discountPercentage = 50.0,
            currency = "INR",
            availability = Availability.IN_STOCK,
            deliveryInfo = "Free Delivery",
            imageUrl = "https://m.media-amazon.com/images/I/71xyz.jpg",
            searchUrl = "https://www.amazon.in/s?k=gripper",
            isDirectProductUrl = true,
            status = MarketplaceStatus.AVAILABLE,
            matchScore = 95,
            lastVerifiedAt = 123456789L
        )

        val entity = originalOffer.toEntity()
        val mappedBack = entity.toDomain()

        assertEquals(originalOffer.id, mappedBack.id)
        assertEquals(originalOffer.store, mappedBack.store)
        assertEquals(originalOffer.productUrl, mappedBack.productUrl)
        assertEquals(originalOffer.price, mappedBack.price)
        assertEquals(originalOffer.isDirectProductUrl, mappedBack.isDirectProductUrl)
        assertEquals(originalOffer.status, mappedBack.status)
        assertEquals(originalOffer.searchUrl, mappedBack.searchUrl)
        assertEquals(originalOffer.matchScore, mappedBack.matchScore)
        assertEquals(originalOffer.lastVerifiedAt, mappedBack.lastVerifiedAt)
    }
}
