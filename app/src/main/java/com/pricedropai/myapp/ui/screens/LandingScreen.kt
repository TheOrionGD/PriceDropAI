package com.pricedropai.myapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * High-aesthetic landing page featuring:
 * 7 distinct segments with skeuomorphic embossed cards and glassmorphic translucent layouts.
 */
@Composable
fun LandingScreen(
    onNavigateToAuth: () -> Unit
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // -------------------------------------------------------------
            // SEGMENT 1: Hero Banner (Glassmorphism & Radial Neon Glow)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFF818CF8).copy(alpha = 0.6f), Color.Transparent)
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Pill badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF312E81).copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, Color(0xFF6366F1))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "NEXT-GEN SHOPPING INTELLIGENCE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC),
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    Text(
                        text = "Track Every Price.\nSave Every Penny.",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 36.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Cross-store price comparison across Indian e-commerce with real-time drop alerts, AI copilot, and automated card offers.",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = onNavigateToAuth,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Get Started Free", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }

            // -------------------------------------------------------------
            // SEGMENT 2: Live Trending Drops (Skeuomorphic embossed Cards)
            // -------------------------------------------------------------
            SectionTitle(title = "Live Trending Price Drops", subtitle = "Monitored across Flipkart, Amazon, and more")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SkeuomorphicDealCard(
                    store = "Amazon India",
                    storeBadgeColor = Color(0xFFFF9900),
                    productName = "Sony WH-1000XM5 Wireless Headphones",
                    originalPrice = "₹34,990",
                    currentPrice = "₹26,990",
                    dropAmount = "₹8,000 DROP (23% OFF)",
                    timeAgo = "18m ago"
                )
                SkeuomorphicDealCard(
                    store = "Flipkart",
                    storeBadgeColor = Color(0xFF2874F0),
                    productName = "Apple iPhone 15 (128 GB) - Black",
                    originalPrice = "₹79,900",
                    currentPrice = "₹64,999",
                    dropAmount = "₹14,901 DROP (19% OFF)",
                    timeAgo = "42m ago"
                )
            }

            Spacer(Modifier.height(28.dp))

            // -------------------------------------------------------------
            // SEGMENT 3: Supported Store Ecosystem (Glassmorphism Badges)
            // -------------------------------------------------------------
            SectionTitle(title = "Supported Retail Ecosystem", subtitle = "Track prices from any verified Indian retailer")

            val stores = listOf(
                "Flipkart" to Color(0xFF2874F0),
                "Amazon" to Color(0xFFFF9900),
                "Croma" to Color(0xFF00E676),
                "Reliance Digital" to Color(0xFFE53935),
                "Vijay Sales" to Color(0xFFD32F2F),
                "Tata CLiQ" to Color(0xFF8E24AA),
                "Nykaa" to Color(0xFFEC407A)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Display in 2 column chunks
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stores.take(4).forEach { (name, color) ->
                        GlassStoreBadge(storeName = name, accentColor = color)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stores.drop(4).forEach { (name, color) ->
                        GlassStoreBadge(storeName = name, accentColor = color)
                    }
                    GlassStoreBadge(storeName = "+ More Stores", accentColor = Color(0xFF6366F1))
                }
            }

            Spacer(Modifier.height(28.dp))

            // -------------------------------------------------------------
            // SEGMENT 4: AI Power Engine Highlights (Glassmorphic Grid)
            // -------------------------------------------------------------
            SectionTitle(title = "AI-Driven Smart Features", subtitle = "Next-level shopping copilot at your fingertips")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassFeatureCard(
                    icon = Icons.Default.SmartToy,
                    iconTint = Color(0xFF38BDF8),
                    title = "Autonomous AI Copilot",
                    description = "Chat naturally with the copilot. Ask for product comparisons, budget picks, and historical price projections."
                )
                GlassFeatureCard(
                    icon = Icons.Default.Analytics,
                    iconTint = Color(0xFFA78BFA),
                    title = "AI Sentiment & Review Synthesis",
                    description = "Scans thousands of real user reviews to distill clear pros, cons, and honest durability insights."
                )
                GlassFeatureCard(
                    icon = Icons.Default.CreditCard,
                    iconTint = Color(0xFF34D399),
                    title = "Bank Offer & Payment Engine",
                    description = "Instantly calculates effective prices considering HDFC, ICICI, SBI card promotions and cashbacks."
                )
            }

            Spacer(Modifier.height(28.dp))

            // -------------------------------------------------------------
            // SEGMENT 5: Interactive Simulator (Skeuomorphic Calculation Card)
            // -------------------------------------------------------------
            SectionTitle(title = "Interactive Savings Preview", subtitle = "See how much you save on typical electronics")

            SkeuomorphicSavingsCard()

            Spacer(Modifier.height(28.dp))

            // -------------------------------------------------------------
            // SEGMENT 6: Community Trust & Stats
            // -------------------------------------------------------------
            SectionTitle(title = "Shopper Confidence", subtitle = "Real community metrics & transparency")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatBadge(
                    number = "₹1.4 Cr+",
                    label = "Total Savings Tracked",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    number = "99.8%",
                    label = "Alert Accuracy",
                    modifier = Modifier.weight(1f)
                )
                StatBadge(
                    number = "< 15s",
                    label = "Drop Detection",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(32.dp))

            // -------------------------------------------------------------
            // SEGMENT 7: Final Call to Action (CTA)
            // -------------------------------------------------------------
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Ready to start saving?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Set up your secure local access in seconds. No cloud credentials required.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onNavigateToAuth,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Continue to Login / Register", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Helper Sub-Components for Landing Screen
// -------------------------------------------------------------------------

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SkeuomorphicDealCard(
    store: String,
    storeBadgeColor: Color,
    productName: String,
    originalPrice: String,
    currentPrice: String,
    dropAmount: String,
    timeAgo: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = storeBadgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, storeBadgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = store,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = storeBadgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = timeAgo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = productName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = currentPrice,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = originalPrice,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = dropAmount,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GlassStoreBadge(storeName: String, accentColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = storeName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun GlassFeatureCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SkeuomorphicSavingsCard() {
    var trackerSlider by remember { mutableFloatStateOf(45000f) }
    val estimatedSavings = (trackerSlider * 0.22f).toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Annual Gadgets / Shopping Budget",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "₹${trackerSlider.toInt()}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )

            Slider(
                value = trackerSlider,
                onValueChange = { trackerSlider = it },
                valueRange = 10000f..150000f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Estimated AI PriceDrop Savings:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹$estimatedSavings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                    Text(
                        text = "avg 22%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
fun StatBadge(number: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
