package com.pricedropai.myapp.data.repository

import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.core.model.PriceSnapshot
import com.pricedropai.myapp.core.model.Product
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.local.entity.PriceSnapshotEntity
import com.pricedropai.myapp.data.local.entity.SearchHistoryEntity
import com.pricedropai.myapp.data.mapper.Mappers
import com.pricedropai.myapp.data.mapper.Mappers.toDomain
import com.pricedropai.myapp.data.mapper.Mappers.toEntity
import com.pricedropai.myapp.data.remote.datasource.BackendProductDataSource
import com.pricedropai.myapp.data.remote.datasource.ProductDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ProductRepository(
    private val database: PriceDropDatabase,
    private val remoteDataSource: ProductDataSource = BackendProductDataSource()
) {
    private val productDao = database.productDao()
    private val priceSnapshotDao = database.priceSnapshotDao()
    private val searchHistoryDao = database.searchHistoryDao()

    fun observeProduct(productId: String): Flow<Product?> {
        return productDao.observeProductWithOffers(productId).map { it?.toDomain() }
    }

    fun observeRecentProducts(limit: Int = 20): Flow<List<Product>> {
        return productDao.observeRecentProducts(limit).map { list -> list.map { it.toDomain() } }
    }

    fun observeRecentSearches(limit: Int = 10): Flow<List<String>> {
        return searchHistoryDao.observeRecentSearches(limit).map { list -> list.map { it.query } }
    }

    fun observePriceSnapshots(productId: String): Flow<List<PriceSnapshot>> {
        return priceSnapshotDao.observeSnapshots(productId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getPriceSnapshots(productId: String): List<PriceSnapshot> = withContext(Dispatchers.IO) {
        priceSnapshotDao.getSnapshots(productId).map { it.toDomain() }
    }

    suspend fun recordSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            searchHistoryDao.insertSearch(SearchHistoryEntity(query = query.trim()))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchHistoryDao.clearAll()
    }

    /**
     * Offline-first product search and fetch.
     * 1. Emits cached result from Room if present.
     * 2. Fetches live data from remote stores.
     * 3. Persists live result + price snapshots to Room.
     * 4. Emits updated data.
     * 5. If remote fails and cache exists, maintains cache. If no cache, emits error. Never fakes data.
     */
    fun searchAndSyncProduct(query: String): Flow<Result<Product>> = flow {
        val cleanQuery = com.pricedropai.myapp.core.parser.UrlProductParser.extractSearchQuery(query).ifBlank { query.trim() }
        val estimatedId = cleanQuery.lowercase().replace("[^a-z0-9]+".toRegex(), "-").take(60)

        // 1. Emit cached data if available in Room and relevant to the query
        val cachedWithOffers = productDao.getProductWithOffers(estimatedId)
            ?: productDao.observeRecentProducts(10).let { null }
        var emittedCache = false

        if (cachedWithOffers != null) {
            val cachedDomain = cachedWithOffers.toDomain()
            val queryWords = cleanQuery.lowercase().split("\\s+".toRegex()).filter { it.length > 1 }
            val titleLower = cachedDomain.title.lowercase()
            val matches = if (queryWords.isEmpty()) 1 else queryWords.count { titleLower.contains(it) }
            val matchRatio = if (queryWords.isEmpty()) 1.0 else matches.toDouble() / queryWords.size

            if (matchRatio >= 0.5) {
                emittedCache = true
                emit(Result.success(cachedDomain))
            }
        }

        // 2. Query live data from remote data source
        val remoteResult = remoteDataSource.searchProducts(cleanQuery)

        remoteResult.fold(
            onSuccess = { products ->
                if (products.isNotEmpty()) {
                    val liveProduct = products.first()

                    // Save to Room DB under its native ID
                    val productEntity = liveProduct.toEntity()
                    val offerEntities = liveProduct.stores.map { it.toEntity() }
                    productDao.saveProductWithOffers(productEntity, offerEntities)

                    // Also save under estimated query ID as alias so future offline/cache lookups find this exact product
                    if (liveProduct.id != estimatedId) {
                        val aliasEntity = productEntity.copy(id = estimatedId)
                        val aliasOffers = offerEntities.map { 
                            it.copy(id = "${estimatedId}_${it.store.name.lowercase()}", productId = estimatedId) 
                        }
                        productDao.saveProductWithOffers(aliasEntity, aliasOffers)
                    }

                    // Record live price snapshots for each store with a verified price
                    liveProduct.stores.forEach { offer ->
                        if (offer.price != null && offer.price > 0) {
                            recordPriceSnapshot(
                                productId = liveProduct.id,
                                store = offer.store,
                                price = offer.price,
                                availability = offer.availability
                            )
                        }
                    }

                    // Production integrity: Do not manufacture synthetic baseline history points.
                    // Price history snapshots reflect only genuine verified observations.

                    // Save to search history
                    recordSearchQuery(cleanQuery)

                    // Emit live updated domain product
                    emit(Result.success(liveProduct))
                } else {
                    if (cachedWithOffers == null) {
                        emit(Result.failure(NoSuchElementException("No live offers found for '$cleanQuery'. Please verify the search terms.")))
                    }
                }
            },
            onFailure = { error ->
                if (cachedWithOffers == null) {
                    emit(Result.failure(error))
                }
            }
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Directly records a verified price snapshot.
     */
    suspend fun recordPriceSnapshot(
        productId: String,
        store: com.pricedropai.myapp.core.model.Store,
        price: Double,
        availability: Availability = Availability.IN_STOCK
    ): Long = withContext(Dispatchers.IO) {
        val existing = priceSnapshotDao.getSnapshots(productId)
        val lastSameStore = existing.lastOrNull { it.store == store }
        val now = System.currentTimeMillis()
        if (lastSameStore != null && lastSameStore.price == price && (now - lastSameStore.timestamp) < 300_000L) {
            return@withContext lastSameStore.id
        }
        priceSnapshotDao.insertSnapshot(
            PriceSnapshotEntity(
                productId = productId,
                store = store,
                price = price,
                timestamp = now,
                availability = availability
            )
        )
    }
}
