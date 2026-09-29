package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
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
import com.example.network.ActiveSymbol
import com.example.ui.components.FintechCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.*
import com.example.ui.viewmodel.MainViewModel

@Composable
fun MarketsScreen(
    viewModel: MainViewModel,
    onMarketSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSymbols by viewModel.webSocketClient.activeSymbols.collectAsState()
    val scannerSignals by viewModel.scannerSignals.collectAsState()
    val selectedSymbol by viewModel.selectedSymbol.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = listOf("ALL", "SYNTHETICS", "FOREX", "CRYPTO", "COMMODITIES")

    // Filtered symbols
    val displaySymbols = remember(activeSymbols, searchQuery, selectedCategory) {
        val baseList = if (activeSymbols.isNotEmpty()) {
            activeSymbols
        } else {
            // Default discovery list while API initial payload arrives
            listOf(
                ActiveSymbol("R_10", "Volatility 10 Index", "synthetic_index", "", false, 1204.50, 0.001),
                ActiveSymbol("R_25", "Volatility 25 Index", "synthetic_index", "", false, 2410.80, 0.001),
                ActiveSymbol("R_50", "Volatility 50 Index", "synthetic_index", "", false, 310.20, 0.001),
                ActiveSymbol("R_75", "Volatility 75 Index", "synthetic_index", "", false, 58210.15, 0.01),
                ActiveSymbol("R_100", "Volatility 100 Index", "synthetic_index", "", false, 942.30, 0.01),
                ActiveSymbol("1HZ10V", "Volatility 10 (1s) Index", "synthetic_index", "", false, 810.12, 0.01),
                ActiveSymbol("1HZ100V", "Volatility 100 (1s) Index", "synthetic_index", "", false, 1045.60, 0.01),
                ActiveSymbol("frxEURUSD", "EUR/USD", "forex", "major_pairs", false, 1.0845, 0.00001),
                ActiveSymbol("frxGBPUSD", "GBP/USD", "forex", "major_pairs", false, 1.2980, 0.00001),
                ActiveSymbol("cryBTCUSD", "BTC/USD", "cryptocurrency", "", false, 68450.0, 0.01),
                ActiveSymbol("cryETHUSD", "ETH/USD", "cryptocurrency", "", false, 3540.0, 0.01)
            )
        }

        baseList.filter { sym ->
            val matchQuery = searchQuery.isBlank() ||
                sym.displayName.contains(searchQuery, ignoreCase = true) ||
                sym.symbol.contains(searchQuery, ignoreCase = true)

            val matchCategory = when (selectedCategory) {
                "ALL" -> true
                "SYNTHETICS" -> sym.market.contains("synthetic", ignoreCase = true) || sym.symbol.startsWith("R_") || sym.symbol.startsWith("1HZ")
                "FOREX" -> sym.market.contains("forex", ignoreCase = true) || sym.symbol.startsWith("frx")
                "CRYPTO" -> sym.market.contains("crypto", ignoreCase = true) || sym.symbol.startsWith("cry")
                "COMMODITIES" -> sym.market.contains("commodit", ignoreCase = true)
                else -> true
            }
            matchQuery && matchCategory
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search markets, synthetic indices, pairs...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("market_search_input"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = CardBorderDark,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyanPrimary else SurfaceDark)
                        .border(1.dp, if (isSelected) CyanPrimary else CardBorderDark, RoundedCornerShape(8.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) BackgroundDark else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Header Row for Scanner Table (PDF Section 22)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "MARKET / PRICE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(text = "AI SIGNAL / EV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(text = "STATUS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        }

        HorizontalDivider(color = DividerColor, thickness = 1.dp)

        // Live Scanner List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(displaySymbols) { sym ->
                val tick = viewModel.webSocketClient.getLatestTick(sym.symbol)
                val signal = scannerSignals[sym.symbol]
                val isSelected = sym.symbol == selectedSymbol

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectSymbol(sym.symbol)
                            onMarketSelected(sym.symbol)
                        }
                        .border(
                            1.dp,
                            if (isSelected) CyanPrimary else CardBorderDark,
                            RoundedCornerShape(12.dp)
                        )
                        .testTag("market_item_${sym.symbol}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: Symbol Name and Live Quote
                        Column(modifier = Modifier.weight(1.3f)) {
                            Text(
                                text = sym.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val quoteStr = if (tick != null) {
                                String.format(java.util.Locale.US, "%.4f", tick.quote)
                            } else if (sym.spot != null) {
                                String.format(java.util.Locale.US, "%.4f", sym.spot)
                            } else {
                                "DATA UNAVAILABLE"
                            }
                            Text(
                                text = quoteStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (tick != null) CyanPrimary else TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Center: Signal and Expected Value
                        Column(
                            modifier = Modifier.weight(1.2f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (signal != null) {
                                Text(
                                    text = signal.contractType,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (signal.decision == "TRADE") ProfitGreen else TextSecondary
                                )
                                Text(
                                    text = "${if (signal.expectedValuePct >= 0) "+" else ""}${String.format(java.util.Locale.US, "%.1f", signal.expectedValuePct)}% EV",
                                    fontSize = 10.sp,
                                    color = if (signal.expectedValuePct >= 0) ProfitGreen else LossRed,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    text = "SCANNING",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Right: Status Pill & Arrow
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.weight(1f)
                        ) {
                            val statusText = when {
                                tick == null && sym.spot == null -> "NO DATA"
                                signal?.decision == "TRADE" -> "SIGNAL"
                                signal?.decision == "NO TRADE" -> "NO TRADE"
                                else -> "WATCH"
                            }
                            val statusBg = when (statusText) {
                                "SIGNAL" -> ProfitGreenBg
                                "NO TRADE" -> LossRedBg
                                "NO DATA" -> SurfaceVariantDark
                                else -> SurfaceVariantDark
                            }
                            val statusColor = when (statusText) {
                                "SIGNAL" -> ProfitGreen
                                "NO TRADE" -> LossRed
                                "NO DATA" -> TextMuted
                                else -> BlueSecondary
                            }

                            StatusPill(text = statusText, backgroundColor = statusBg, textColor = statusColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
