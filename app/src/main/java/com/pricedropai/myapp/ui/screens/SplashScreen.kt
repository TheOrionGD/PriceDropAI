package com.pricedropai.myapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.data.remote.datasource.BackendProductDataSource
import com.pricedropai.myapp.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    var backendStatus by remember { mutableStateOf("Initializing app...") }

    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.05f else 0.75f,
        animationSpec = tween(
            durationMillis = 1200,
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    // Splash timer: Strictly 3 seconds max, never blocking UI on backend network
    LaunchedEffect(Unit) {
        startAnimation = true

        // Fire asynchronous, non-blocking cold boot ping to Render backend immediately
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val request = Request.Builder()
                    .url("${BackendProductDataSource.BASE_URL}/api/health")
                    .get()
                    .build()
                client.newCall(request).execute().close()
            } catch (_: Exception) {
                // Background warm-up ping sent; backend wakes up concurrently during onboarding
            }
        }

        // Quick 3-second visual progression
        backendStatus = "Loading local engine..."
        delay(1000)
        backendStatus = "Preparing multi-store connections..."
        delay(1000)
        backendStatus = "Ready!"
        delay(1000)

        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scaleAnim.value)
                .padding(32.dp)
        ) {
            // Glowing skeuomorphic icon badge with radiant breathing aura
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .glowBackdrop(glowColor = Color(0xFF6366F1), minAlpha = 0.35f, maxAlpha = 0.85f)
                    .clip(RoundedCornerShape(28.dp))
                    .neonGlow(
                        color = Color(0xFF818CF8),
                        secondaryColor = Color(0xFF38BDF8),
                        shape = RoundedCornerShape(28.dp),
                        borderWidth = 2.dp
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF818CF8),
                                Color(0xFF4F46E5),
                                Color(0xFF312E81)
                            )
                        )
                    )
                    .shimmerSweep(shimmerColor = Color.White.copy(alpha = 0.2f), durationMillis = 1800),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = "PriceDrop AI Logo",
                    tint = Color.White,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "PriceDrop AI",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Real-Time Multi-Store Price Tracking & AI Engine",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(44.dp))

            LinearProgressIndicator(
                modifier = Modifier
                    .width(160.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = backendStatus,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = "v2.1.7 • Powered by Render & Local AI Cache",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}
