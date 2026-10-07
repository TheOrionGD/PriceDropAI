package com.example.pricedropai.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Reviews
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pricedropai.core.model.ReviewSummary

@Composable
fun ReviewSummarySection(
    summary: ReviewSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        text = "AI Review Summary",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (summary.isAvailable && summary.sentimentScore != null) {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
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

            Spacer(Modifier.height(8.dp))

            if (!summary.isAvailable || (summary.pros.isEmpty() && summary.cons.isEmpty())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Reviews,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Verified review summary currently unavailable for this item.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (summary.totalAnalyzed != null) {
                    Text(
                        text = "Based on ${summary.totalAnalyzed} verified customer reviews",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }

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
        }
    }
}
