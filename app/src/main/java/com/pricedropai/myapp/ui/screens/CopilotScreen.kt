package com.pricedropai.myapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pricedropai.myapp.ui.TrackerViewModel
import kotlinx.coroutines.launch

@Composable
fun CopilotScreen(
    viewModel: TrackerViewModel,
    onProductSelect: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val suggestionChips = listOf(
        "Which store is cheapest?",
        "Show price history trend",
        "Should I buy now or wait?",
        "Summarize customer reviews"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "PriceDrop AI Copilot",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Shopping intelligence backed by live data and price history",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestionChips.forEach { chipText ->
                SuggestionChip(
                    onClick = {
                        viewModel.sendChatMessage(chipText)
                        scope.launch { listState.animateScrollToItem(viewModel.chatMessages.size) }
                    },
                    label = { Text(chipText, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(viewModel.chatMessages, key = { it.id }) { msg ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (msg.isUser) 16.dp else 2.dp,
                            bottomEnd = if (msg.isUser) 2.dp else 16.dp
                        ),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(
                            text = msg.text,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 13.sp,
                            color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }

                    if (!msg.isUser && !msg.suggestedSearchQuery.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        AssistChip(
                            onClick = { onProductSelect(msg.suggestedSearchQuery) },
                            label = { Text("Compare prices for: ${msg.suggestedSearchQuery}", fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (viewModel.isBotTyping) {
                item {
                    Text(
                        text = "AI Copilot is analyzing verified product data...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask about store prices, history, or deals...", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendChatMessage(text)
                        scope.launch { listState.animateScrollToItem(viewModel.chatMessages.size) }
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
