package com.pricedropai.myapp.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pricedropai.myapp.MainActivity
import com.pricedropai.myapp.R
import com.pricedropai.myapp.SessionManager
import com.pricedropai.myapp.data.local.InAppNotificationCategory
import com.pricedropai.myapp.data.local.NotificationSeverity

object NotificationHelper {

    // Notification Channel IDs
    const val CHANNEL_PRICE_DROPS = "price_drop_alerts_channel"
    private const val CHANNEL_PRICE_DROPS_NAME = "Price Drop Alerts"

    const val CHANNEL_APP_MESSAGES = "app_messages_channel"
    private const val CHANNEL_APP_MESSAGES_NAME = "Activity & In-App Alerts"

    // Backward-compatible alias for existing callers
    const val CHANNEL_ID = CHANNEL_PRICE_DROPS

    fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            // 1. Price Drop Alerts Channel (High Priority Heads-Up Alert)
            if (manager.getNotificationChannel(CHANNEL_PRICE_DROPS) == null) {
                val channel = NotificationChannel(
                    CHANNEL_PRICE_DROPS,
                    CHANNEL_PRICE_DROPS_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Instant notifications when tracked products hit or drop below your target price"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 150, 300)
                    enableLights(true)
                    lightColor = Color.GREEN
                    setSound(soundUri, audioAttributes)
                }
                manager.createNotificationChannel(channel)
            }

            // 2. Activity & In-App Messages Channel (Heads-up status for live searches, Copilot & activity)
            if (manager.getNotificationChannel(CHANNEL_APP_MESSAGES) == null) {
                val channel = NotificationChannel(
                    CHANNEL_APP_MESSAGES,
                    CHANNEL_APP_MESSAGES_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Phone notifications for search completions, target alert confirmations, Copilot advice & system activity"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200)
                    enableLights(true)
                    lightColor = Color.CYAN
                    setSound(soundUri, audioAttributes)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Posts a phone notification when a price drop is detected.
     */
    fun showPriceDropNotification(
        context: Context,
        productTitle: String,
        currentPrice: Double,
        targetPrice: Double,
        storeName: String?,
        notificationId: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val sessionManager = SessionManager(context)
        if (!sessionManager.isPhoneNotificationsEnabled()) {
            return
        }

        ensureChannelCreated(context)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_PRODUCT_QUERY", productTitle)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val storeInfo = if (!storeName.isNullOrBlank()) " on $storeName" else ""
        val title = "🔥 Price Drop Detected: $productTitle"
        val message = "Now ₹${"%,.0f".format(currentPrice)}$storeInfo (Your Target: ₹${"%,.0f".format(targetPrice)})"

        val builder = NotificationCompat.Builder(context, CHANNEL_PRICE_DROPS)
            .setSmallIcon(R.drawable.ic_stat_pricedrop)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nTap to compare all live store offers now."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROMO)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "Compare Offers",
                pendingIntent
            )

        if (sessionManager.isSoundEnabled()) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setSilent(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    /**
     * Posts an App-to-Phone system notification for messages generated from in-app notifications
     * (Live searches, alert arming, Copilot consultations, or subsystem events).
     */
    fun showAppMessageNotification(
        context: Context,
        title: String,
        message: String,
        category: InAppNotificationCategory? = null,
        severity: NotificationSeverity = NotificationSeverity.INFO,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val sessionManager = SessionManager(context)
        if (!sessionManager.isPhoneNotificationsEnabled()) {
            return
        }

        ensureChannelCreated(context)

        // Tapping the notification takes user directly to NotificationsCenterScreen inside MainActivity
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_NOTIFICATIONS_CENTER", true)
            putExtra("NOTIFICATION_TITLE", title)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val categoryPrefix = when (category) {
            InAppNotificationCategory.SEARCHES -> "🔍 Live Search"
            InAppNotificationCategory.ALERTS -> "🎯 Price Alert"
            InAppNotificationCategory.COPILOT -> "🤖 AI Copilot"
            InAppNotificationCategory.SYSTEM -> "⚡ PriceDrop AI"
            InAppNotificationCategory.ALL, null -> "📢 PriceDrop AI"
        }

        val formattedTitle = "$categoryPrefix: $title"

        val builder = NotificationCompat.Builder(context, CHANNEL_APP_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_pricedrop)
            .setContentTitle(formattedTitle)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(formattedTitle)
                    .bigText("$message\n\nTap to view activity history.")
            )
            .setPriority(
                if (severity == NotificationSeverity.ALERT) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_view,
                "View Activity",
                pendingIntent
            )

        if (sessionManager.isSoundEnabled()) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
        } else {
            builder.setSilent(true)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }
}
