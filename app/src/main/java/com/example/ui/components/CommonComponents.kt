package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun FintechCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = SurfaceDark,
    borderColor: Color = CardBorderDark,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    backgroundColor: Color,
    textColor: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun LiveDataBadge(isLive: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isLive) ProfitGreenBg else LossRedBg)
            .border(1.dp, if (isLive) ProfitGreen else LossRed, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isLive) ProfitGreen else LossRed)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isLive) "LIVE DATA" else "DATA UNAVAILABLE",
            color = if (isLive) ProfitGreen else LossRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun MetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary,
    subValue: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            fontFamily = FontFamily.Monospace
        )
        if (subValue != null) {
            Text(
                text = subValue,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun InteractiveTickChart(
    prices: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = CyanPrimary,
    areaGradient: List<Color> = listOf(CyanGlow, Color.Transparent)
) {
    if (prices.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(SurfaceVariantDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "STREAMING TICKS...",
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        return
    }

    val minPrice = prices.minOrNull() ?: 0.0
    val maxPrice = prices.maxOrNull() ?: 1.0
    val priceRange = if (maxPrice - minPrice == 0.0) 1.0 else maxPrice - minPrice

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "HIGH: ${String.format(java.util.Locale.US, "%.4f", maxPrice)}",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "CURRENT: ${String.format(java.util.Locale.US, "%.4f", prices.last())}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = lineColor,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "LOW: ${String.format(java.util.Locale.US, "%.4f", minPrice)}",
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark.copy(alpha = 0.5f))
        ) {
            val width = size.width
            val height = size.height
            val stepX = width / (prices.size - 1)

            val path = Path()
            val fillPath = Path()

            prices.forEachIndexed { i, p ->
                val normY = 1.0 - ((p - minPrice) / priceRange)
                val x = i * stepX
                val y = (normY * (height * 0.8f) + (height * 0.1f)).toFloat()

                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw Area
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(areaGradient)
            )

            // Draw Line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw current point pulse
            val lastY = (1.0 - ((prices.last() - minPrice) / priceRange)) * (height * 0.8f) + (height * 0.1f)
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = Offset(width, lastY.toFloat())
            )
        }
    }
}

@Composable
fun DigitDistributionChart(
    counts: IntArray,
    percentages: DoubleArray,
    minDigit: Int,
    maxDigit: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "LAST DIGIT FREQUENCY (0 - 9)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            for (d in 0..9) {
                val pct = if (d < percentages.size) percentages[d] else 10.0
                val isHot = d == maxDigit
                val isCold = d == minDigit
                val barColor = when {
                    isHot -> ProfitGreen
                    isCold -> BlueSecondary
                    else -> CyanPrimary.copy(alpha = 0.6f)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "${pct.toInt()}%",
                        fontSize = 9.sp,
                        color = if (isHot || isCold) barColor else TextMuted,
                        fontWeight = if (isHot) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height((pct * 2.5).coerceIn(4.0, 70.0).dp)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$d",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHot) ProfitGreen else if (isCold) BlueSecondary else TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun ResponsibleTradingBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceVariantDark.copy(alpha = 0.6f))
            .border(1.dp, CardBorderDark, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Risk Warning",
            tint = WarningAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Trading involves substantial risk of loss. Past algorithmic performance does not guarantee future results. AI analysis is an assistive tool.",
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 14.sp
        )
    }
}
