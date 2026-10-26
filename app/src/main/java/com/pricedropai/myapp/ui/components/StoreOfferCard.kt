package com.pricedropai.myapp.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.core.model.Availability
import com.pricedropai.myapp.core.model.StoreOffer
import com.pricedropai.myapp.ui.theme.*

@Composable
fun StoreOfferCard(
    offer: StoreOffer,
    isLowest: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val storeColor = when (offer.store) {
        com.pricedropai.myapp.core.model.Store.AMAZON -> Color(0xFFFF9900)
        com.pricedropai.myapp.core.model.Store.FLIPKART -> Color(0xFF2874F0)
        com.pricedropai.myapp.core.model.Store.MEESHO -> Color(0xFFE40066)
        com.pricedropai.myapp.core.model.Store.MYNTRA -> Color(0xFFFF3F6C)
        com.pricedropai.myapp.core.model.Store.CROMA -> Color(0xFF00E676)
        com.pricedropai.myapp.core.model.Store.RELIANCE_DIGITAL -> Color(0xFFE42529)
        com.pricedropai.myapp.core.model.Store.JIOMART -> Color(0xFF0F3E99)
        com.pricedropai.myapp.core.model.Store.TATACLIQ -> Color(0xFF8B5CF6)
        com.pricedropai.myapp.core.model.Store.AJIO -> Color(0xFF0284C7)
        com.pricedropai.myapp.core.model.Store.NYKAA -> Color(0xFFFC2779)
        com.pricedropai.myapp.core.model.Store.BLINKIT -> Color(0xFFF59E0B)
        com.pricedropai.myapp.core.model.Store.EBAY -> Color(0xFFE53238)
        com.pricedropai.myapp.core.model.Store.WALMART -> Color(0xFF0071DC)
        com.pricedropai.myapp.core.model.Store.TARGET -> Color(0xFFCC0000)
    }

    val isUnavailable = offer.price == null || offer.price <= 0.0 ||
            offer.availability == Availability.OUT_OF_STOCK ||
            offer.deliveryInfo?.contains("Unavailable", ignoreCase = true) == true

    val effectiveIsLowest = isLowest && !isUnavailable && (offer.price ?: 0.0) > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .bounceClick(targetScale = 0.98f) {
                try {
                    val rawUrl = offer.productUrl.trim()
                    val targetUrl = when {
                        rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                        rawUrl.startsWith("//") -> "https:$rawUrl"
                        else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(offer.productId, "UTF-8")}+${offer.store.displayName}"
                    }
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackSearch = "https://www.google.com/search?q=${java.net.URLEncoder.encode(offer.productId, "UTF-8")}+${offer.store.displayName}"
                        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackSearch)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(fallbackIntent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot open store: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .then(
                if (effectiveIsLowest) {
                    Modifier
                        .glowBackdrop(glowColor = Color(0xFF10B981), minAlpha = 0.12f, maxAlpha = 0.38f)
                        .neonGlow(
                            color = Color(0xFF10B981),
                            secondaryColor = storeColor,
                            shape = RoundedCornerShape(16.dp),
                            borderWidth = 1.3.dp
                        )
                        .shimmerSweep(shimmerColor = Color.White.copy(alpha = 0.08f), durationMillis = 2000)
                } else if (isUnavailable) {
                    Modifier
                } else {
                    Modifier.glowBackdrop(glowColor = storeColor, minAlpha = 0.04f, maxAlpha = 0.18f)
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnavailable) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (effectiveIsLowest) 1.5.dp else 1.dp,
            color = when {
                effectiveIsLowest -> Color(0xFF10B981)
                isUnavailable -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = storeColor.copy(alpha = if (isUnavailable) 0.08f else 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = offer.store.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isUnavailable) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    if (effectiveIsLowest) {
                        Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "LOWEST",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isUnavailable) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "(Product Unavailable..)",
                                color = Color(0xFFEF4444),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (isUnavailable) {
                    Text(
                        text = "Not in store catalog / Out of stock",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                } else {
                    val delivery = offer.deliveryInfo
                    if (!delivery.isNullOrBlank()) {
                        Text(
                            text = "🚚 $delivery",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    val stockStatusText = when (offer.availability) {
                        Availability.IN_STOCK -> "In Stock"
                        Availability.OUT_OF_STOCK -> "Currently Unavailable"
                        Availability.LIMITED_STOCK -> "Limited Stock Available"
                        Availability.UNKNOWN -> "Availability not reported"
                    }

                    Text(
                        text = stockStatusText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    if (!isUnavailable && offer.price != null) {
                        Text(
                            text = "₹${"%,.0f".format(offer.price)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = if (effectiveIsLowest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (offer.originalPrice != null && offer.originalPrice > offer.price) {
                            Text(
                                text = "MRP ₹${"%,.0f".format(offer.originalPrice)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "₹0",
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Text(
                            text = "(Product Unavailable..)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Visit Store",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isUnavailable) 0.4f else 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
