package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun MarketDetailSheet(
    symbol: String,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tickHistory = viewModel.getTickHistory(symbol)
    val prices = tickHistory.map { it.quote }
    val latestTick = viewModel.webSocketClient.getLatestTick(symbol)
    val signal by viewModel.currentSignal.collectAsState()
    val digitStats by viewModel.currentDigitStats.collectAsState()
    val proposal by viewModel.currentProposal.collectAsState()
    val openOrders by viewModel.openOrders.collectAsState()

    val symbolOrders = openOrders.filter { it.symbol == symbol }

    var selectedContractType by remember { mutableStateOf("DIGITDIFF") }
    val contractTypes = listOf("DIGITDIFF", "DIGITMATCH", "DIGITEVEN", "DIGITODD", "CALL", "PUT")

    FintechCard(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
        backgroundColor = SurfaceDark
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = viewModel.getSymbolDisplayName(symbol),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Symbol: $symbol",
                    fontSize = 11.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Live Price & Tick Chart
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(text = "LIVE DERIV QUOTE", fontSize = 10.sp, color = TextMuted)
                            val q = latestTick?.quote
                            Text(
                                text = if (q != null) String.format(java.util.Locale.US, "%.4f", q) else "DATA UNAVAILABLE",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (q != null) CyanPrimary else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (latestTick != null) {
                            StatusPill(
                                text = "LAST DIGIT: ${latestTick.lastDigit}",
                                backgroundColor = CyanGlow,
                                textColor = CyanPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    InteractiveTickChart(prices = prices)
                }
            }

            // Digit Distribution Chart (PDF Section 7)
            item {
                val stats = digitStats
                if (stats != null && stats.totalCount > 0) {
                    DigitDistributionChart(
                        counts = stats.digitCounts,
                        percentages = stats.digitPercentages,
                        minDigit = stats.minFrequentDigit,
                        maxDigit = stats.maxFrequentDigit
                    )
                }
            }

            // Contract Type Selector
            item {
                Column {
                    Text(
                        text = "SELECT CONTRACT TYPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        contractTypes.take(3).forEach { cType ->
                            val sel = cType == selectedContractType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) CyanPrimary else SurfaceVariantDark)
                                    .clickable {
                                        selectedContractType = cType
                                        viewModel.webSocketClient.requestProposal(
                                            symbol = symbol,
                                            contractType = cType,
                                            amount = 2.0,
                                            barrier = if (cType.startsWith("DIGIT")) "5" else null
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cType,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) BackgroundDark else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        contractTypes.takeLast(3).forEach { cType ->
                            val sel = cType == selectedContractType
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) CyanPrimary else SurfaceVariantDark)
                                    .clickable {
                                        selectedContractType = cType
                                        viewModel.webSocketClient.requestProposal(
                                            symbol = symbol,
                                            contractType = cType,
                                            amount = 2.0,
                                            barrier = if (cType.startsWith("DIGIT")) "5" else null
                                        )
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cType,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) BackgroundDark else TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Live Proposal Information from Deriv
            item {
                val prop = proposal
                FintechCard(backgroundColor = SurfaceVariantDark) {
                    Text(
                        text = "LIVE CONTRACT PROPOSAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (prop != null) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            MetricBox(label = "Stake", value = "$${String.format(java.util.Locale.US, "%.2f", prop.stake)}", modifier = Modifier.weight(1f))
                            MetricBox(label = "Payout", value = "$${String.format(java.util.Locale.US, "%.2f", prop.payout)}", modifier = Modifier.weight(1f))
                            MetricBox(
                                label = "Potential Net",
                                value = "+$${String.format(java.util.Locale.US, "%.2f", prop.payout - prop.stake)}",
                                valueColor = ProfitGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (prop.longcode.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = prop.longcode,
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Requesting live contract pricing from Deriv WebSocket...",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Action Buttons (PDF Section 23)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sig = signal
                            if (sig != null) {
                                viewModel.executeManualOrder(sig, isPaper = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BadgePaperBg),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("detail_paper_trade_button")
                    ) {
                        Text("PAPER TRADE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val sig = signal
                            if (sig != null) {
                                viewModel.executeManualOrder(sig, isPaper = false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = BackgroundDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("detail_live_trade_button")
                    ) {
                        Text("TRADE REAL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Open Positions for this symbol
            if (symbolOrders.isNotEmpty()) {
                item {
                    Text(
                        text = "ACTIVE CONTRACTS (${symbolOrders.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    symbolOrders.forEach { ord ->
                        FintechCard(backgroundColor = SurfaceVariantDark) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = ord.orderId, fontSize = 11.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                                StatusPill(text = ord.result, backgroundColor = if (ord.result == "WON") ProfitGreenBg else WarningAmberBg)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
