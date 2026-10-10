package com.pricedropai.thas.data.local.dao

import androidx.room.*
import com.pricedropai.thas.core.model.Store
import com.pricedropai.thas.data.local.entity.PriceSnapshotEntity
import com.pricedropai.thas.data.local.entity.ProductEntity
import com.pricedropai.thas.data.local.entity.SearchHistoryEntity
import com.pricedropai.thas.data.local.entity.StoreOfferEntity
import com.pricedropai.thas.data.local.entity.TrackedProductEntity
import kotlinx.coroutines.flow.Flow

data class ProductWithOffers(
    @Embedded val product: ProductEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val offers: List<StoreOfferEntity>
)

@Dao
interface ProductDao {
    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    fun observeProductWithOffers(productId: String): Flow<ProductWithOffers?>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :productId LIMIT 1")
    suspend fun getProductWithOffers(productId: String): ProductWithOffers?

    @Transaction
    @Query("SELECT * FROM products ORDER BY lastUpdated DESC LIMIT :limit")
    fun observeRecentProducts(limit: Int = 20): Flow<List<ProductWithOffers>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<StoreOfferEntity>)

    @Query("DELETE FROM store_offers WHERE productId = :productId")
    suspend fun deleteOffersForProduct(productId: String)

    @Transaction
    suspend fun saveProductWithOffers(product: ProductEntity, offers: List<StoreOfferEntity>) {
        insertProduct(product)
        deleteOffersForProduct(product.id)
        insertOffers(offers)
    }

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProduct(productId: String)
}

@Dao
interface StoreOfferDao {
    @Query("SELECT * FROM store_offers WHERE productId = :productId")
    fun observeOffersForProduct(productId: String): Flow<List<StoreOfferEntity>>

    @Query("SELECT * FROM store_offers WHERE productId = :productId")
    suspend fun getOffersForProduct(productId: String): List<StoreOfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffer(offer: StoreOfferEntity)
}

@Dao
interface PriceSnapshotDao {
    @Query("SELECT * FROM price_snapshots WHERE productId = :productId ORDER BY timestamp ASC")
    fun observeSnapshots(productId: String): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots WHERE productId = :productId ORDER BY timestamp ASC")
    suspend fun getSnapshots(productId: String): List<PriceSnapshotEntity>

    @Query("SELECT * FROM price_snapshots WHERE productId = :productId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    suspend fun getSnapshotsSince(productId: String, sinceTimestamp: Long): List<PriceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PriceSnapshotEntity): Long

    @Query("SELECT * FROM price_snapshots WHERE productId = :productId AND store = :store ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshotForStore(productId: String, store: Store): PriceSnapshotEntity?
}

@Dao
interface TrackedProductDao {
    @Query("SELECT * FROM tracked_products ORDER BY createdAt DESC")
    fun observeTrackedProducts(): Flow<List<TrackedProductEntity>>

    @Query("SELECT * FROM tracked_products WHERE enabled = 1 ORDER BY createdAt DESC")
    fun observeActiveTrackedProducts(): Flow<List<TrackedProductEntity>>

    @Query("SELECT * FROM tracked_products WHERE enabled = 1")
    suspend fun getAllActiveTrackedProducts(): List<TrackedProductEntity>

    @Query("SELECT * FROM tracked_products WHERE id = :id LIMIT 1")
    suspend fun getTrackedById(id: Long): TrackedProductEntity?

    @Query("SELECT * FROM tracked_products WHERE productId = :productId LIMIT 1")
    suspend fun getTrackedByProductId(productId: String): TrackedProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracked(item: TrackedProductEntity): Long

    @Update
    suspend fun updateTracked(item: TrackedProductEntity)

    @Query("UPDATE tracked_products SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE tracked_products SET currentPrice = :currentPrice, lastChecked = :lastChecked WHERE id = :id")
    suspend fun updatePriceCheck(id: Long, currentPrice: Double, lastChecked: Long)

    @Query("UPDATE tracked_products SET lastNotifiedPrice = :notifiedPrice, lastNotificationTimestamp = :timestamp WHERE id = :id")
    suspend fun updateNotificationState(id: Long, notifiedPrice: Double, timestamp: Long)

    @Query("DELETE FROM tracked_products WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Delete
    suspend fun delete(item: TrackedProductEntity)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSearches(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearchById(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
