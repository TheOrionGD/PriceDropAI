package com.pricedropai.myapp.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.remote.datasource.MultiStoreProductDataSource
import com.pricedropai.myapp.data.repository.ProductRepository
import com.pricedropai.myapp.data.repository.WatchlistRepository
import com.pricedropai.myapp.notification.NotificationHelper
import java.util.concurrent.TimeUnit

class PriceCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "PriceDropPeriodicCheck"
        private const val TAG = "PriceCheckWorker"

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = 30,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background price check work...")
        val database = PriceDropDatabase.getInstance(context)
        val watchlistRepo = WatchlistRepository(database)
        val productRepo = ProductRepository(database, MultiStoreProductDataSource())

        val activeTrackers = try {
            watchlistRepo.getActiveTrackedProducts()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read watchlist from DB: ${e.message}")
            return Result.retry()
        }

        if (activeTrackers.isEmpty()) {
            Log.d(TAG, "No active products to monitor.")
            return Result.success()
        }

        var hasNetworkFailures = false

        for (item in activeTrackers) {
            try {
                val remoteResult = MultiStoreProductDataSource().searchProducts(item.productTitle)
                val products = remoteResult.getOrNull()

                if (!products.isNullOrEmpty()) {
                    val product = products.first()
                    val lowestOffer = product.lowestOffer
                    val currentPrice = lowestOffer?.price

                    if (currentPrice != null && currentPrice > 0) {
                        // 1. Record snapshot in Room
                        productRepo.recordPriceSnapshot(
                            productId = product.id,
                            store = lowestOffer.store,
                            price = currentPrice,
                            availability = lowestOffer.availability
                        )

                        // 2. Update tracker last checked price
                        watchlistRepo.updatePriceCheck(item.id, currentPrice)

                        // 3. Evaluate notification trigger with duplicate suppression
                        if (watchlistRepo.shouldTriggerNotification(item, currentPrice)) {
                            NotificationHelper.showPriceDropNotification(
                                context = context,
                                productTitle = item.productTitle,
                                currentPrice = currentPrice,
                                targetPrice = item.targetPrice,
                                storeName = lowestOffer.store.displayName,
                                notificationId = item.id.toInt()
                            )

                            // 4. Update notified state
                            watchlistRepo.markNotified(item.id, currentPrice)
                            Log.d(TAG, "Notification sent for ${item.productTitle} at ₹$currentPrice")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Price check failed for '${item.productTitle}': ${e.localizedMessage}")
                hasNetworkFailures = true
            }
        }

        return if (hasNetworkFailures && activeTrackers.size == 1) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
