package com.pricedropai.thas.data.local.entity

import androidx.room.*
import com.pricedropai.thas.core.model.Availability
import com.pricedropai.thas.core.model.Store

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val category: String? = null,
    val brand: String? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val reviewSummaryJson: String? = null,
    val paymentOffersJson: String? = null,
    val variantsJson: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "store_offers",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["productId"]), Index(value = ["productId", "store"], unique = true)]
)
data class StoreOfferEntity(
    @PrimaryKey
    val id: String,
    val productId: String,
    val store: Store,
    val productUrl: String,
    val price: Double?,
    val originalPrice: Double?,
    val discountPercentage: Double?,
    val currency: String = "INR",
    val availability: Availability = Availability.IN_STOCK,
    val deliveryInfo: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "price_snapshots",
    indices = [Index(value = ["productId"]), Index(value = ["productId", "timestamp"])]
)
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: String,
    val store: Store,
    val price: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val availability: Availability = Availability.IN_STOCK
)

@Entity(
    tableName = "tracked_products",
    indices = [Index(value = ["productId"])]
)
data class TrackedProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: String,
    val productTitle: String,
    val imageUrl: String? = null,
    val store: Store? = null,
    val currentPrice: Double,
    val targetPrice: Double,
    val lastNotifiedPrice: Double? = null,
    val lastNotificationTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastChecked: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
