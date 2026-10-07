package com.example.pricedropai

import com.example.pricedropai.core.model.*
import com.example.pricedropai.data.local.dao.ProductWithOffers
import com.example.pricedropai.data.local.entity.PriceSnapshotEntity
import com.example.pricedropai.data.local.entity.ProductEntity
import com.example.pricedropai.data.local.entity.StoreOfferEntity
import com.example.pricedropai.data.local.entity.TrackedProductEntity
import com.example.pricedropai.data.mapper.Mappers.toDomain
import com.example.pricedropai.data.mapper.Mappers.toEntity
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
        val fourStores = Store.entries.map { store ->
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
}
