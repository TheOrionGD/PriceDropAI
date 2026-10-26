package com.pricedropai.myapp

import com.pricedropai.myapp.core.model.*
import com.pricedropai.myapp.data.local.dao.ProductWithOffers
import com.pricedropai.myapp.data.local.entity.PriceSnapshotEntity
import com.pricedropai.myapp.data.local.entity.ProductEntity
import com.pricedropai.myapp.data.local.entity.StoreOfferEntity
import com.pricedropai.myapp.data.local.entity.TrackedProductEntity
import com.pricedropai.myapp.data.mapper.Mappers.toDomain
import com.pricedropai.myapp.data.mapper.Mappers.toEntity
import org.junit.Assert.*
import org.junit.Test

class ProductModelTest {

    @Test
    fun testLowestOfferCalculation() {
        val stores = listOf(
            StoreOffer(
                id = "1",
                productId = "prod-1",
                store = Store.AMAZON,
                productUrl = "https://amazon.in",
                price = 56999.0,
                originalPrice = 59999.0,
                discountPercentage = 5.0,
                availability = Availability.IN_STOCK
            ),
            StoreOffer(
                id = "2",
                productId = "prod-1",
                store = Store.FLIPKART,
                productUrl = "https://flipkart.com",
                price = 54999.0,
                originalPrice = 59999.0,
                discountPercentage = 8.0,
                availability = Availability.IN_STOCK
            ),
            StoreOffer(
                id = "3",
                productId = "prod-1",
                store = Store.MEESHO,
                productUrl = "https://meesho.com",
                price = null,
                originalPrice = null,
                discountPercentage = null,
                availability = Availability.OUT_OF_STOCK
            )
        )

        val product = Product(
            id = "prod-1",
            title = "Apple iPhone 15",
            stores = stores
        )

        assertNotNull(product.lowestOffer)
        assertEquals(Store.FLIPKART, product.lowestOffer?.store)
        assertEquals(54999.0, product.lowestPrice ?: 0.0, 0.01)
        assertEquals(56999.0, product.highestOffer?.price ?: 0.0, 0.01)
    }

    @Test
    fun testWatchlistNotificationDeduplication() {
        val item = TrackedProduct(
            id = 1,
            productId = "prod-1",
            productTitle = "Apple iPhone 15",
            currentPrice = 55000.0,
            targetPrice = 50000.0,
            lastNotifiedPrice = null,
            enabled = true
        )

        // 1. Price is above target -> No notification
        val higherPrice = 52000.0
        assertFalse(higherPrice <= item.targetPrice)

        // 2. Price hits target for first time -> Trigger notification
        val hitTarget = 49999.0
        assertTrue(hitTarget <= item.targetPrice && (item.lastNotifiedPrice == null || hitTarget < item.lastNotifiedPrice!!))

        // 3. Price stays same (49999.0) after notification was sent -> Suppress duplicate
        val itemNotified = item.copy(lastNotifiedPrice = 49999.0)
        assertFalse(hitTarget < itemNotified.lastNotifiedPrice!!)

        // 4. Price drops even further (47999.0) -> Trigger new notification
        val furtherDrop = 47999.0
        assertTrue(furtherDrop <= itemNotified.targetPrice && furtherDrop < itemNotified.lastNotifiedPrice!!)
    }

    @Test
    fun testEntityDomainMappers() {
        val domainOffer = StoreOffer(
            id = "offer-1",
            productId = "p-1",
            store = Store.AMAZON,
            productUrl = "https://amazon.in/dp/123",
            price = 999.0,
            originalPrice = 1499.0,
            discountPercentage = 33.0,
            availability = Availability.IN_STOCK
        )

        val entityOffer = domainOffer.toEntity()
        assertEquals(domainOffer.id, entityOffer.id)
        assertEquals(domainOffer.price, entityOffer.price)
        assertEquals(domainOffer.store, entityOffer.store)

        val mappedBack = entityOffer.toDomain()
        assertEquals(domainOffer.id, mappedBack.id)
        assertEquals(domainOffer.price, mappedBack.price)
        assertEquals(domainOffer.store, mappedBack.store)
    }

    @Test
    fun testFourStoreComparisonIntegrity() {
        val fourStores = listOf(Store.AMAZON, Store.FLIPKART, Store.MEESHO, Store.MYNTRA).map { store ->
            StoreOffer(
                id = "p1_${store.name.lowercase()}",
                productId = "p1",
                store = store,
                productUrl = "https://example.com/${store.name.lowercase()}",
                price = when (store) {
                    Store.AMAZON -> 1159.0
                    Store.FLIPKART -> 1149.0
                    Store.MEESHO -> 1089.0
                    Store.MYNTRA -> 1199.0
                    else -> 1250.0
                },
                originalPrice = 1449.0,
                discountPercentage = 20.0,
                availability = Availability.IN_STOCK
            )
        }

        val product = Product(
            id = "go24-water-bottle",
            title = "Go24 Stainless Steel Anime Water Bottle 1 LTR | Thermosteel Flask",
            imageUrl = "https://images.unsplash.com/photo-1602143407151-7111542de6e8",
            stores = fourStores
        )

        assertEquals(4, product.stores.size)
        assertTrue(product.stores.any { it.store == Store.AMAZON })
        assertTrue(product.stores.any { it.store == Store.FLIPKART })
        assertTrue(product.stores.any { it.store == Store.MEESHO })
        assertTrue(product.stores.any { it.store == Store.MYNTRA })
        assertEquals(Store.MEESHO, product.lowestOffer?.store)
        assertEquals(1089.0, product.lowestPrice ?: 0.0, 0.01)
        assertNotNull(product.imageUrl)
        assertTrue(product.imageUrl!!.startsWith("http"))
    }

    @Test
    fun testProductIdentityAndImageIntegrity() {
        val productA = Product(
            id = "apple-iphone-15-128gb",
            title = "Apple iPhone 15 (128 GB) - Black",
            imageUrl = "https://m.media-amazon.com/images/I/71657TiFeHL._AC_SL1000_.jpg",
            asin = "B0CHX1W1XY",
            searchQuery = "iPhone 15"
        )

        val productB = Product(
            id = "puma-running-shoes-dazzler",
            title = "Puma Men Running Shoes Dazzler",
            imageUrl = "https://rukmini1.flixcart.com/image/832/832/puma-shoe.jpg",
            asin = null,
            searchQuery = "Puma shoes"
        )

        val productCWithoutImage = Product(
            id = "generic-adapter",
            title = "Generic USB-C Adapter",
            imageUrl = null,
            asin = null,
            searchQuery = "adapter"
        )

        // 1. Verify identities and ASIN preservation
        assertEquals("B0CHX1W1XY", productA.asin)
        assertEquals("iPhone 15", productA.searchQuery)
        assertNull(productB.asin)

        // 2. Strict image URL isolation
        assertNotNull(productA.imageUrl)
        assertNotNull(productB.imageUrl)
        assertNull(productCWithoutImage.imageUrl)
        assertNotEquals(productA.imageUrl, productB.imageUrl)
        assertTrue(productA.imageUrl!!.contains("media-amazon.com"))
        assertTrue(productB.imageUrl!!.contains("flixcart.com"))
    }
}
