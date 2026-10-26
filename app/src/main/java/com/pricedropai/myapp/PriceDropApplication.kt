package com.pricedropai.myapp

import android.app.Application
import com.pricedropai.myapp.data.local.InAppNotificationManager
import com.pricedropai.myapp.notification.NotificationHelper

/**
 * Main Application class for PriceDrop AI.
 * Initializes notification channels and hooks real-time in-app notification dispatching
 * directly to Android system phone notifications.
 */
class PriceDropApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannelCreated(this)
        InAppNotificationManager.initialize(this)
    }
}
