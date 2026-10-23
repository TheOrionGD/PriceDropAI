package com.pricedropai.myapp.data.local

import androidx.compose.ui.graphics.Color
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
 * No hardcoded or stacked static dummy notices.
 */
object InAppNotificationManager {

    private val _notifications = MutableStateFlow<List<InAppNotificationItem>>(emptyList())
    val notifications: StateFlow<List<InAppNotificationItem>> = _notifications.asStateFlow()

    private val _activeBanner = MutableStateFlow<InAppNotificationItem?>(null)
    val activeBanner: StateFlow<InAppNotificationItem?> = _activeBanner.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun dismissBanner() {
        _activeBanner.value = null
    }

    fun postNotification(
        title: String,
        description: String,
        category: InAppNotificationCategory,
        iconType: NotificationIconType = NotificationIconType.SYSTEM,
        accentColor: Color = Color(0xFF6366F1),
        severity: NotificationSeverity = NotificationSeverity.INFO
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

        // Show immediately as top center banner
        _activeBanner.value = newItem

        // Prepend to show newest on top, keep up to 40 recent real user activity events
        _notifications.value = listOf(newItem) + _notifications.value.take(39)
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
