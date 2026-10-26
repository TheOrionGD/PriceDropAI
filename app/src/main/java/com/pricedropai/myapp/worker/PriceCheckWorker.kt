package com.pricedropai.myapp.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.pricedropai.myapp.SessionManager
import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.data.local.InAppNotificationCategory
import com.pricedropai.myapp.data.local.InAppNotificationManager
import com.pricedropai.myapp.data.local.NotificationIconType
import com.pricedropai.myapp.data.local.NotificationSeverity
import com.pricedropai.myapp.data.local.PriceDropDatabase
import com.pricedropai.myapp.data.remote.datasource.BackendProductDataSource
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

        /**
         * Schedules or reschedules the periodic price check worker with the requested interval.
         * Enforces network and battery constraints, exponential backoff, and updates existing jobs.
         */
        fun schedulePeriodicCheck(
            context: Context,
            intervalHours: Long = SessionManager(context).getPriceCheckInterval().hours
        ) {
            val validHours = intervalHours.coerceAtLeast(3L) // WorkManager periodic minimum is 15 mins; app supports 3h, 6h, 12h, 24h
            val flexMinutes = if (validHours <= 3L) 15L else 30L

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<PriceCheckWorker>(
                repeatInterval = validHours,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
                flexTimeInterval = flexMinutes,
                flexTimeIntervalUnit = TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicRequest
            )

            Log.i(TAG, "Scheduled unique periodic work '$WORK_NAME' every $validHours hours (flex $flexMinutes mins)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background production price check...")
        val sessionManager = SessionManager(context)
        val database = PriceDropDatabase.getInstance(context)
        val watchlistRepo = WatchlistRepository(database)

        // Production requirement: Query the actual backend with no synthetic offline multipliers
        val backendDataSource = BackendProductDataSource(fallbackDataSource = null)
        val productRepo = ProductRepository(database, backendDataSource)

        val activeTrackers = try {
            watchlistRepo.getActiveTrackedProducts()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read watchlist from DB: ${e.message}")
            return Result.retry()
        }

        if (activeTrackers.isEmpty()) {
            Log.d(TAG, "No active products in watchlist to monitor.")
            sessionManager.setLastCheckTimestamp(System.currentTimeMillis())
            sessionManager.setLastCheckDiagnostics("Watchlist is currently empty.")
            return Result.success()
        }

        var productsChecked = 0
        var productsUpdated = 0
        var productsSkipped = 0
        var apiFailures = 0
        var alertsSent = 0

        for (item in activeTrackers) {
            productsChecked++
            try {
                // 1. First attempt direct product lookup by persisted backend product identifier
                val directProduct = backendDataSource.getProduct(item.productId).getOrNull()
                
                // 2. If direct lookup returns null, perform clean search query
                val product = directProduct ?: run {
                    val searchList = backendDataSource.searchProducts(item.productTitle).getOrNull()
                    searchList?.firstOrNull { candidate ->
                        candidate.id == item.productId ||
                            candidate.title.contains(item.productTitle, ignoreCase = true) ||
                            item.productTitle.contains(candidate.title.take(25), ignoreCase = true)
                    } ?: searchList?.firstOrNull()
                }
                if (product == null) {
                    Log.w(TAG, "Backend search for '${item.productTitle}' yielded no verified match. Preserving previous price.")
                    productsSkipped++
                    continue
                }

                // 3. Resolve targeted store offer or lowest verified price
                val targetOffer = if (item.store != null) {
                    product.stores.firstOrNull { it.store == item.store && it.price != null && it.price > 0 && it.availability != Availability.OUT_OF_STOCK }
                        ?: product.lowestOffer
                } else {
                    product.lowestOffer
                }

                val observedPrice = targetOffer?.price
                val observedStore = targetOffer?.store ?: item.store ?: com.pricedropai.myapp.core.model.Store.AMAZON

                if (observedPrice != null && observedPrice > 0) {
                    // 4. Record genuine verified price snapshot in Room (with duplicate suppression)
                    productRepo.recordPriceSnapshot(
                        productId = product.id,
                        store = observedStore,
                        price = observedPrice,
                        availability = targetOffer?.availability ?: Availability.IN_STOCK
                    )

                    // 5. Update tracker's last checked price & timestamp
                    watchlistRepo.updatePriceCheck(item.id, observedPrice)
                    productsUpdated++

                    // 6. Evaluate notification trigger with duplicate suppression
                    if (watchlistRepo.shouldTriggerNotification(item, observedPrice)) {
                        alertsSent++
                        NotificationHelper.showPriceDropNotification(
                            context = context,
                            productTitle = item.productTitle,
                            currentPrice = observedPrice,
                            targetPrice = item.targetPrice,
                            storeName = observedStore.displayName,
                            notificationId = item.id.toInt()
                        )

                        // Update in-app activity center
                        val storeInfo = observedStore.displayName.let { " on $it" }
                        InAppNotificationManager.postNotification(
                            title = "🔥 Price Drop: ${item.productTitle.take(30)}",
                            description = "Now ₹${"%,.0f".format(observedPrice)}$storeInfo (Target: ₹${"%,.0f".format(item.targetPrice)})",
                            category = InAppNotificationCategory.ALERTS,
                            iconType = NotificationIconType.ALERT,
                            accentColor = androidx.compose.ui.graphics.Color(0xFF10B981),
                            severity = NotificationSeverity.ALERT,
                            postToPhone = false // Avoid duplicate system alert
                        )

                        // Persist notified state to avoid duplicate notifications on identical checks
                        watchlistRepo.markNotified(item.id, observedPrice)
                        Log.i(TAG, "Notification delivered for '${item.productTitle}' at ₹$observedPrice (Target: ₹${item.targetPrice})")
                    }
                } else {
                    Log.w(TAG, "No valid in-stock price observed for '${item.productTitle}'.")
                    productsSkipped++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Price check error for '${item.productTitle}': ${e.message}")
                apiFailures++
            }
        }

        // Record safe operational diagnostics in local preferences
        val now = System.currentTimeMillis()
        val diagSummary = "Checked $productsChecked items: $productsUpdated updated, $productsSkipped skipped, $apiFailures errors, $alertsSent alerts."
        sessionManager.setLastCheckTimestamp(now)
        sessionManager.setLastCheckDiagnostics(diagSummary)
        Log.i(TAG, "Price check complete. $diagSummary")

        return if (apiFailures > 0 && productsUpdated == 0 && activeTrackers.isNotEmpty()) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
