package com.pricedropai.myapp.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.data.local.InAppNotificationItem
import com.pricedropai.myapp.data.local.InAppNotificationManager
import com.pricedropai.myapp.data.local.NotificationIconType
import com.pricedropai.myapp.ui.theme.bounceClick
import com.pricedropai.myapp.ui.theme.glowBackdrop
import com.pricedropai.myapp.ui.theme.neonGlow
import kotlinx.coroutines.delay

/**
 * Top-center real-time floating banner displaying in-app activity notifications
 * immediately as the user acts in the app. Matches selected app theme and accent.
 * Auto-dismisses smoothly after 4 seconds or on user tap.
 */
@Composable
fun TopNotificationBanner(
    bannerItem: InAppNotificationItem?,
    onBannerClick: () -> Unit,
    onDismiss: () -> Unit
) {
    // Auto-dismiss after 4.2 seconds
    LaunchedEffect(bannerItem?.id) {
        if (bannerItem != null) {
            delay(4200)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = bannerItem != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(250)),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (bannerItem != null) {
            val iconVector = when (bannerItem.iconType) {
                NotificationIconType.SEARCH -> Icons.Outlined.Search
                NotificationIconType.ALERT -> Icons.Outlined.NotificationsActive
                NotificationIconType.COPILOT -> Icons.Outlined.SmartToy
                NotificationIconType.TUNING -> Icons.Outlined.Tune
                NotificationIconType.TRASH -> Icons.Outlined.DeleteOutline
                NotificationIconType.HISTORY -> Icons.Outlined.History
                NotificationIconType.SYSTEM -> Icons.Outlined.Info
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick(targetScale = 0.98f) { onBannerClick() }
                    .clickable { onBannerClick() }
                    .glowBackdrop(glowColor = MaterialTheme.colorScheme.primary, minAlpha = 0.15f, maxAlpha = 0.45f)
                    .neonGlow(
                        color = MaterialTheme.colorScheme.primary,
                        secondaryColor = bannerItem.accentColor,
                        shape = RoundedCornerShape(18.dp),
                        borderWidth = 1.2.dp
                    ),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bannerItem.accentColor.copy(alpha = 0.15f))
                            .border(1.dp, bannerItem.accentColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = bannerItem.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = bannerItem.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = bannerItem.description,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
