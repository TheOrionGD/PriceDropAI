package com.pricedropai.myapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.core.model.RatingDistribution
import com.pricedropai.myapp.core.model.ReviewSummary

@Composable
fun ReviewSummarySection(
    summary: ReviewSummary,
    avgRating: Double? = null,
    totalReviews: Int? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Review Analytics & Sentiment",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (summary.sentimentScore != null && summary.sentimentScore > 0) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "${summary.sentimentScore}% Positive",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 1. Overall Rating & Total Reviews
            val displayRating = avgRating ?: (summary.sentimentScore?.let { it / 20.0 })
            val displayCount = totalReviews ?: summary.totalAnalyzed

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (displayRating != null && displayRating > 0) "%.1f".format(displayRating) else "N/A",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (displayRating != null && displayRating > 0) "Overall Customer Rating" else "Rating Not Provided",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (displayCount != null && displayCount > 0) "Based on ${"%,d".format(displayCount)} customer reviews" else "No verified reviews recorded",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!summary.analysisStatus.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = summary.analysisStatus.replace("_", " "),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Rating Distribution (5★ to 1★)
            if (summary.ratingDistribution != null) {
                Text(
                    text = "Rating Breakdown",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                val dist = summary.ratingDistribution
                val starRows = listOf(
                    Triple("5★", dist.fiveStar?.percentage ?: 0.0, dist.fiveStar?.count ?: 0),
                    Triple("4★", dist.fourStar?.percentage ?: 0.0, dist.fourStar?.count ?: 0),
                    Triple("3★", dist.threeStar?.percentage ?: 0.0, dist.threeStar?.count ?: 0),
                    Triple("2★", dist.twoStar?.percentage ?: 0.0, dist.twoStar?.count ?: 0),
                    Triple("1★", dist.oneStar?.percentage ?: 0.0, dist.oneStar?.count ?: 0)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    starRows.forEach { (starLabel, percentage, count) ->
                        val animatedProgress by animateFloatAsState(
                            targetValue = (percentage / 100.0).toFloat().coerceIn(0f, 1f),
                            animationSpec = tween(durationMillis = 600),
                            label = "star_progress_$starLabel"
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = starLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when (starLabel) {
                                    "5★", "4★" -> Color(0xFF10B981)
                                    "3★" -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${percentage.toInt()}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(34.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            // 3. Feature Sentiment Scorecards
            val featureScores = summary.featureScores
            if (featureScores != null && (featureScores.buildQuality != null || featureScores.valueForMoney != null || featureScores.performance != null || featureScores.durability != null)) {
                Text(
                    text = "Feature Sentiment Analysis",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureCard(
                        icon = Icons.Outlined.Build,
                        title = "Build Quality",
                        score = featureScores.buildQuality,
                        modifier = Modifier.weight(1f)
                    )
                    FeatureCard(
                        icon = Icons.Outlined.AttachMoney,
                        title = "Value",
                        score = featureScores.valueForMoney,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureCard(
                        icon = Icons.Outlined.Speed,
                        title = "Performance",
                        score = featureScores.performance,
                        modifier = Modifier.weight(1f)
                    )
                    FeatureCard(
                        icon = Icons.Outlined.Shield,
                        title = "Durability",
                        score = featureScores.durability,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // 4. Pros and Cons
            if (!summary.isAvailable || (summary.pros.isEmpty() && summary.cons.isEmpty())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Reviews,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = summary.evidenceCoverage ?: "Verified buyer sentiment currently unavailable for this listing.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (summary.pros.isNotEmpty()) {
                    Text("Pros:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Spacer(Modifier.height(4.dp))
                    summary.pros.forEach { pro ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("✓ ", color = Color(0xFF10B981), fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text(pro, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                        }
                    }
                }

                if (summary.cons.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Cons:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    Spacer(Modifier.height(4.dp))
                    summary.cons.forEach { con ->
                        Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("✕ ", color = Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 12.sp)
                            Text(con, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
                        }
                    }
                }
            }

            // Evidence Coverage & Source Footer
            if (!summary.evidenceCoverage.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Evidence: ${summary.evidenceCoverage}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    score: Int?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                if (score != null) {
                    Text(
                        text = "$score/100",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        color = if (score >= 75) Color(0xFF10B981) else if (score >= 50) Color(0xFFF59E0B) else Color(0xFFEF4444)
                    )
                } else {
                    Text(
                        text = "N/A",
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
