package com.example.pricedropai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class PriceAlertWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val dbHelper = LocalDbHelper(context)
        val watchlist = dbHelper.getAllWatchlist()

        for (item in watchlist) {
            try {
                // context pass pannanum: (context, query)
                val report = LocalPriceEngine.fetchProductData(context, item.productName)
                if (report.lowestPrice <= item.targetPrice) {
                    showNotification(
                        "Price Drop Alert! 🔥",
                        "${item.productName} is now ₹${"%,.0f".format(report.lowestPrice)} (Target: ₹${"%,.0f".format(item.targetPrice)})"
                    )
                }
            } catch (e: Exception) {
                // Skip if any network error during background check
            }
        }
        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val channelId = "price_alert_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Price Drop Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}