package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.TradeOrderEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var selectedView by remember { mutableStateOf(0) } // 0: OPEN POSITIONS, 1: EXECUTION LOG, 2: PERFORMANCE
    val views = listOf("OPEN POSITIONS", "EXECUTION LOG", "ANALYTICS")

    val allOrders by viewModel.allOrders.collectAsState()
    val openOrders by viewModel.openOrders.collectAsState()
    val activeSimulated by viewModel.executionEngine.activeSimulatedContracts.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // View Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            views.forEachIndexed { index, title ->
                val isSelected = selectedView == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyanPrimary else Color.Transparent)
                        .clickable { selectedView = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) BackgroundDark else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedView) {
            0 -> OpenPositionsView(
                openOrders = openOrders + activeSimulated.filter { ord -> openOrders.none { it.orderId == ord.orderId } },
                onEmergencySell = { ord ->
                    viewModel.webSocketClient.sellContract(ord.id)
                }
            )
            1 -> ExecutionLogView(orders = allOrders)
            2 -> AnalyticsView(orders = allOrders)
        }
    }
}

@Composable
private fun OpenPositionsView(
    openOrders: List<TradeOrderEntity>,
    onEmergencySell: (TradeOrderEntity) -> Unit
) {
    if (openOrders.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NO OPEN CONTRACTS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active live or paper positions will appear here with real-time settlement telemetry.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(openOrders) { ord ->
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = ord.symbolName.ifBlank { ord.symbol },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Contract: ${ord.contractType} | ${ord.orderId}",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    StatusPill(
                        text = if (ord.mode == "REAL") "REAL ORDER" else "PAPER CONTRACT",
                        backgroundColor = if (ord.mode == "REAL") BadgeRealBg else BadgePaperBg
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Stake", value = "$${String.format(Locale.US, "%.2f", ord.stake)}", modifier = Modifier.weight(1f))
                    MetricBox(label = "Target Payout", value = "$${String.format(Locale.US, "%.2f", ord.expectedPayout)}", modifier = Modifier.weight(1f))
                    MetricBox(
                        label = "Entry Spot",
                        value = String.format(Locale.US, "%.4f", ord.entryPrice),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = CyanPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MONITORING TICKS...",
                            fontSize = 10.sp,
                            color = CyanPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { onEmergencySell(ord) },
                        colors = ButtonDefaults.buttonColors(containerColor = LossRed),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("EMERGENCY EXIT", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionLogView(orders: List<TradeOrderEntity>) {
    var modeFilter by remember { mutableStateOf("ALL") }
    val filters = listOf("ALL", "REAL", "PAPER")

    val filteredOrders = remember(orders, modeFilter) {
        when (modeFilter) {
            "REAL" -> orders.filter { it.mode == "REAL" }
            "PAPER" -> orders.filter { it.mode == "PAPER" }
            else -> orders
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { f ->
                val sel = f == modeFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (sel) CyanPrimary else SurfaceDark)
                        .clickable { modeFilter = f }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = f,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sel) BackgroundDark else TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("NO RECORDED ORDERS IN LOG", color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredOrders) { ord ->
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ord.entryTime))
                val isWin = ord.result == "WON"
                val isLost = ord.result == "LOST"

                FintechCard(backgroundColor = SurfaceDark) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = ord.symbol, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPill(text = ord.mode, backgroundColor = if (ord.mode == "REAL") BadgeRealBg else BadgePaperBg)
                            }
                            Text(text = "ID: ${ord.orderId} | $timeStr", fontSize = 10.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                        }

                        val resultBg = when {
                            isWin -> ProfitGreenBg
                            isLost -> LossRedBg
                            else -> WarningAmberBg
                        }
                        val resultColor = when {
                            isWin -> ProfitGreen
                            isLost -> LossRed
                            else -> WarningAmber
                        }
                        StatusPill(text = ord.result, backgroundColor = resultBg, textColor = resultColor)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        MetricBox(label = "Stake", value = "$${String.format(Locale.US, "%.2f", ord.stake)}", modifier = Modifier.weight(1f))
                        MetricBox(label = "Contract", value = ord.contractType, modifier = Modifier.weight(1f))
                        val pnl = ord.profitLoss ?: 0.0
                        MetricBox(
                            label = "Profit/Loss",
                            value = "${if (pnl >= 0) "+$" else "-$"}${String.format(Locale.US, "%.2f", kotlin.math.abs(pnl))}",
                            valueColor = if (pnl >= 0) ProfitGreen else LossRed,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (ord.exitReason != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = ord.exitReason,
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsView(orders: List<TradeOrderEntity>) {
    var timeframe by remember { mutableStateOf("ALL TIME") }
    val timeframes = listOf("TODAY", "7 DAYS", "30 DAYS", "ALL TIME")

    val realOrders = orders.filter { it.mode == "REAL" && it.result != "PENDING" }
    val paperOrders = orders.filter { it.mode == "PAPER" && it.result != "PENDING" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                timeframes.forEach { tf ->
                    val sel = tf == timeframe
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) CyanPrimary else SurfaceDark)
                            .clickable { timeframe = tf }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tf,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) BackgroundDark else TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Section 31 Mandate: Strictly Separate LIVE from PAPER results!
        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE REAL-MONEY PERFORMANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    StatusPill(text = "STRICTLY ISOLATED", backgroundColor = BadgeRealBg)
                }

                Spacer(modifier = Modifier.height(12.dp))

                val liveWins = realOrders.count { it.result == "WON" }
                val liveTotal = realOrders.size
                val liveWinRate = if (liveTotal > 0) (liveWins.toDouble() / liveTotal) * 100.0 else 0.0
                val liveNetPnl = realOrders.sumOf { it.profitLoss ?: 0.0 }

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Real Trades", value = "$liveTotal", modifier = Modifier.weight(1f))
                    MetricBox(
                        label = "Win Rate",
                        value = "${liveWinRate.toInt()}%",
                        valueColor = if (liveWinRate >= 50.0) ProfitGreen else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Live Net P/L",
                        value = "${if (liveNetPnl >= 0) "+$" else "-$"}${String.format(Locale.US, "%.2f", kotlin.math.abs(liveNetPnl))}",
                        valueColor = if (liveNetPnl >= 0) ProfitGreen else LossRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            FintechCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PAPER TRADING PERFORMANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    StatusPill(text = "SIMULATED EXECUTION", backgroundColor = BadgePaperBg)
                }

                Spacer(modifier = Modifier.height(12.dp))

                val paperWins = paperOrders.count { it.result == "WON" }
                val paperTotal = paperOrders.size
                val paperWinRate = if (paperTotal > 0) (paperWins.toDouble() / paperTotal) * 100.0 else 0.0
                val paperNetPnl = paperOrders.sumOf { it.profitLoss ?: 0.0 }

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(label = "Paper Trades", value = "$paperTotal", modifier = Modifier.weight(1f))
                    MetricBox(
                        label = "Win Rate",
                        value = "${paperWinRate.toInt()}%",
                        valueColor = if (paperWinRate >= 50.0) ProfitGreen else TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        label = "Paper Net P/L",
                        value = "${if (paperNetPnl >= 0) "+$" else "-$"}${String.format(Locale.US, "%.2f", kotlin.math.abs(paperNetPnl))}",
                        valueColor = if (paperNetPnl >= 0) ProfitGreen else LossRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
