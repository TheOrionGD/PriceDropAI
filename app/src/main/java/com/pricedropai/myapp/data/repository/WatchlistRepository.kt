package com.pricedropai.myapp.data.repository

import com.pricedropai.myapp.core.model.Store
import com.pricedropai.myapp.core.model.TrackedProduct
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.local.entity.TrackedProductEntity
import com.pricedropai.myapp.data.mapper.Mappers.toDomain
import com.pricedropai.myapp.data.mapper.Mappers.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WatchlistRepository(
    private val database: PriceDropDatabase
) {
    private val dao = database.trackedProductDao()

    fun observeWatchlist(): Flow<List<TrackedProduct>> {
        return dao.observeTrackedProducts().map { list -> list.map { it.toDomain() } }
    }

    fun observeActiveWatchlist(): Flow<List<TrackedProduct>> {
        return dao.observeActiveTrackedProducts().map { list -> list.map { it.toDomain() } }
    }

    suspend fun getActiveTrackedProducts(): List<TrackedProduct> = withContext(Dispatchers.IO) {
        dao.getAllActiveTrackedProducts().map { it.toDomain() }
    }

    suspend fun addTrackedProduct(
        productTitle: String,
        targetPrice: Double,
        currentPrice: Double,
        store: Store? = null,
        imageUrl: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val productId = productTitle.lowercase().replace("[^a-z0-9]+".toRegex(), "-").take(60)
        val entity = TrackedProductEntity(
            productId = productId,
            productTitle = productTitle,
            imageUrl = imageUrl,
            store = store,
            currentPrice = currentPrice,
            targetPrice = targetPrice,
            createdAt = System.currentTimeMillis(),
            lastChecked = System.currentTimeMillis(),
            enabled = true
        )
        dao.insertTracked(entity)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    suspend fun deleteTracked(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun updatePriceCheck(id: Long, currentPrice: Double) = withContext(Dispatchers.IO) {
        dao.updatePriceCheck(id, currentPrice, System.currentTimeMillis())
    }

    suspend fun markNotified(id: Long, notifiedPrice: Double) = withContext(Dispatchers.IO) {
        dao.updateNotificationState(id, notifiedPrice, System.currentTimeMillis())
    }

    /**
     * Checks if a price drop notification should be triggered:
     * 1. currentPrice <= targetPrice
     * 2. currentPrice is lower than previously notified price, or no previous notification exists
     */
    fun shouldTriggerNotification(item: TrackedProduct, currentPrice: Double): Boolean {
        if (!item.enabled) return false
        if (currentPrice > item.targetPrice) return false

        val lastNotified = item.lastNotifiedPrice
        return if (lastNotified == null) {
            true
        } else {
            currentPrice < lastNotified
        }
    }
}
