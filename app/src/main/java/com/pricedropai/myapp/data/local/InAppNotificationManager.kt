package com.pricedropai.myapp.data.local

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.pricedropai.myapp.SessionManager
import com.pricedropai.myapp.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class InAppNotificationCategory(val label: String) {
    ALL("All Activity"),
    SEARCHES("Live Searches"),
    ALERTS("Target Alerts"),
    COPILOT("AI Copilot"),
    SYSTEM("System & Network")
}

enum class NotificationSeverity {
    INFO,
    WARNING,
    ALERT
}

enum class NotificationIconType {
    SEARCH,
    ALERT,
    COPILOT,
    SYSTEM,
    TUNING,
    TRASH,
    HISTORY
}

data class InAppNotificationItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: String,
    val iconType: NotificationIconType = NotificationIconType.SYSTEM,
    val accentColor: Color = Color(0xFF6366F1),
    val category: InAppNotificationCategory,
    val isRead: Boolean = false,
    val severity: NotificationSeverity = NotificationSeverity.INFO
)

/**
 * Real-time In-App Notification Manager.
 * Dispatches real-time in-app notifications generated dynamically as the user interacts with the app
 * (searches products, sets price alerts, queries AI copilot, or encounters network/subsystem states).
 * Automatically delivers corresponding App-to-Phone system notifications to the Android status bar / lock screen.
 */
object InAppNotificationManager {

    private const val TAG = "InAppNotifManager"

    private val _notifications = MutableStateFlow<List<InAppNotificationItem>>(emptyList())
    val notifications: StateFlow<List<InAppNotificationItem>> = _notifications.asStateFlow()

    private val _activeBanner = MutableStateFlow<InAppNotificationItem?>(null)
    val activeBanner: StateFlow<InAppNotificationItem?> = _activeBanner.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private var appContext: Context? = null
    private var isPhoneNotificationsEnabled: Boolean = true

    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        try {
            NotificationHelper.ensureChannelCreated(app)
            val sessionManager = SessionManager(app)
            isPhoneNotificationsEnabled = sessionManager.isPhoneNotificationsEnabled()
        } catch (e: Exception) {
            Log.w(TAG, "Initialization failed: ${e.message}")
        }
    }

    fun setPhoneNotificationsEnabled(enabled: Boolean) {
        isPhoneNotificationsEnabled = enabled
    }

    fun isPhoneNotificationsEnabled(): Boolean = isPhoneNotificationsEnabled

    fun dismissBanner() {
        _activeBanner.value = null
    }

    /**
     * Posts an in-app notification item and mirrors it to an Android phone system notification.
     *
     * @param title Title of the event
     * @param description Body description of the event
     * @param category Classification category
     * @param iconType UI icon representation
     * @param accentColor Accent color for in-app banner and icon
     * @param severity Event severity (INFO, WARNING, ALERT)
     * @param postToPhone When true, also delivers an Android notification shade alert on the phone
     */
    fun postNotification(
        title: String,
        description: String,
        category: InAppNotificationCategory,
        iconType: NotificationIconType = NotificationIconType.SYSTEM,
        accentColor: Color = Color(0xFF6366F1),
        severity: NotificationSeverity = NotificationSeverity.INFO,
        postToPhone: Boolean = true
    ) {
        val nowFormatted = "Just now (${timeFormat.format(Date())})"
        val newItem = InAppNotificationItem(
            id = "notif_${System.currentTimeMillis()}_${(100..999).random()}",
            title = title,
            description = description,
            timestamp = nowFormatted,
            iconType = iconType,
            accentColor = accentColor,
            category = category,
            isRead = false,
            severity = severity
        )

        // 1. Show immediately as floating in-app top banner
        _activeBanner.value = newItem

        // 2. Prepend to show newest on top, keep up to 40 recent real user activity events
        _notifications.value = listOf(newItem) + _notifications.value.take(39)

        // 3. Deliver as phone system notification in status bar & lock screen
        if (postToPhone && isPhoneNotificationsEnabled) {
            appContext?.let { ctx ->
                try {
                    NotificationHelper.showAppMessageNotification(
                        context = ctx,
                        title = title,
                        message = description,
                        category = category,
                        severity = severity
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to post phone notification: ${e.message}")
                }
            }
        }
    }

    fun markAsRead(id: String) {
        _notifications.value = _notifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
    }

    fun markAllAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }
}
