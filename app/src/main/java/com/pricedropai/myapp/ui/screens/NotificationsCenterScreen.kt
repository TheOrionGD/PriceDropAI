package com.pricedropai.myapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.pricedropai.myapp.data.local.InAppNotificationCategory
import com.pricedropai.myapp.data.local.InAppNotificationItem
import com.pricedropai.myapp.data.local.InAppNotificationManager
import com.pricedropai.myapp.data.local.NotificationIconType
import com.pricedropai.myapp.data.local.NotificationSeverity
import com.pricedropai.myapp.ui.theme.bounceClick
import com.pricedropai.myapp.ui.theme.glowBackdrop
import com.pricedropai.myapp.ui.theme.neonGlow
import com.pricedropai.myapp.ui.theme.rememberBreathingColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsCenterScreen(
    onBack: () -> Unit
) {
    BackHandler(enabled = true) {
        onBack()
    }

    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf(InAppNotificationCategory.ALL) }

    // Live reactive flow observing real-time notifications created strictly as the user acts in the app
    val notifications by InAppNotificationManager.notifications.collectAsState()

    val unreadCount = notifications.count { !it.isRead }
    val filteredNotifications = remember(notifications, selectedCategory) {
        if (selectedCategory == InAppNotificationCategory.ALL) notifications
        else notifications.filter { it.category == selectedCategory }
    }

    val pulseColor = rememberBreathingColor(
        baseColor = MaterialTheme.colorScheme.primary,
        pulseColor = Color(0xFF10B981),
        durationMillis = 2000
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Activity Notifications",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        if (unreadCount > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.2f, maxAlpha = 0.6f)
                            ) {
                                Text(
                                    text = "$unreadCount New",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.bounceClick(targetScale = 0.92f) { onBack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            InAppNotificationManager.postNotification(
                                title = "Activity Notification Test",
                                description = "Live in-app message mirrored to phone notification tray successfully!",
                                category = InAppNotificationCategory.SYSTEM,
                                iconType = NotificationIconType.SYSTEM,
                                severity = NotificationSeverity.INFO,
                                postToPhone = true
                            )
                            Toast.makeText(context, "Test alert sent to phone notification bar!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.bounceClick(targetScale = 0.92f) { }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationAdd,
                            contentDescription = "Test notification",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                InAppNotificationManager.markAllAsRead()
                                Toast.makeText(context, "All activity marked as read", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.bounceClick(targetScale = 0.92f) {
                                InAppNotificationManager.markAllAsRead()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Mark all read",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                InAppNotificationManager.clearAll()
                                Toast.makeText(context, "Notifications cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.bounceClick(targetScale = 0.92f) {
                                InAppNotificationManager.clearAll()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear all",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Filter Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InAppNotificationCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .bounceClick(targetScale = 0.95f) { selectedCategory = cat }
                            .clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat.label.split(" ").first(),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            if (filteredNotifications.isEmpty()) {
                // Clean Zero-State: only shows real actions taken by the user
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(72.dp)
                                .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.1f, maxAlpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = if (selectedCategory == InAppNotificationCategory.ALL) "No Activity Notifications Yet" else "No ${selectedCategory.label} Activity",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "Real-time in-app alerts appear here and on your phone notification bar when you search products, track price targets, interact with AI Copilot, or when stores update.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(16.dp))

                        FilledTonalButton(
                            onClick = {
                                InAppNotificationManager.postNotification(
                                    title = "Activity Notification Test",
                                    description = "Live in-app message mirrored to phone notification tray successfully!",
                                    category = InAppNotificationCategory.SYSTEM,
                                    iconType = NotificationIconType.SYSTEM,
                                    severity = NotificationSeverity.INFO,
                                    postToPhone = true
                                )
                                Toast.makeText(context, "Test alert sent to phone notification bar!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.bounceClick(targetScale = 0.95f) { }
                        ) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send Test Phone Alert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // In-App Notification Feed List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        // Informative Banner
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.05f, maxAlpha = 0.22f),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Real-time activity feed: dynamically populated based strictly on your in-app actions.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    items(filteredNotifications, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick(targetScale = 0.98f) {
                                    InAppNotificationManager.markAsRead(item.id)
                                }
                                .clickable {
                                    InAppNotificationManager.markAsRead(item.id)
                                }
                                .then(
                                    if (!item.isRead) {
                                        Modifier
                                            .glowBackdrop(glowColor = item.accentColor, minAlpha = 0.08f, maxAlpha = 0.32f)
                                            .neonGlow(
                                                color = item.accentColor,
                                                secondaryColor = pulseColor,
                                                shape = RoundedCornerShape(16.dp),
                                                borderWidth = 1.2.dp
                                            )
                                    } else {
                                        Modifier.glowBackdrop(glowColor = item.accentColor, minAlpha = 0.02f, maxAlpha = 0.12f)
                                    }
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (!item.isRead) item.accentColor.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                val iconVector = when (item.iconType) {
                                    com.pricedropai.myapp.data.local.NotificationIconType.SEARCH -> Icons.Outlined.Search
                                    com.pricedropai.myapp.data.local.NotificationIconType.ALERT -> Icons.Outlined.NotificationsActive
                                    com.pricedropai.myapp.data.local.NotificationIconType.COPILOT -> Icons.Outlined.SmartToy
                                    com.pricedropai.myapp.data.local.NotificationIconType.TUNING -> Icons.Outlined.Tune
                                    com.pricedropai.myapp.data.local.NotificationIconType.TRASH -> Icons.Outlined.DeleteOutline
                                    com.pricedropai.myapp.data.local.NotificationIconType.HISTORY -> Icons.Outlined.History
                                    com.pricedropai.myapp.data.local.NotificationIconType.SYSTEM -> Icons.Outlined.Info
                                }

                                // Icon Avatar
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(item.accentColor.copy(alpha = 0.15f))
                                        .border(1.dp, item.accentColor.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = item.accentColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = item.timestamp,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    Text(
                                        text = item.description,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = item.accentColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = item.category.label,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = item.accentColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (!item.isRead) {
                                            Surface(
                                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "NEW",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
