package com.example.smscosttracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.CategoryTotal
import com.example.smscosttracker.analysis.MonthlyTotal
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import java.util.Locale

private val MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

@Composable
fun CategoryChart(
    rows: List<CategoryTotal>,
    totalMinor: Long,
    currency: String,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) {
        Text(
            "No spending in this period.",
            style = MaterialTheme.typography.bodySmall,
            color = Ledger.Muted,
            modifier = modifier.padding(top = 12.dp)
        )
        return
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CategoryDonutChart(
            rows = rows,
            totalMinor = totalMinor,
            currency = currency
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            rows.forEachIndexed { index, row ->
                CategoryLegendRow(
                    row = row,
                    totalMinor = totalMinor,
                    color = LedgerChartColors[index % LedgerChartColors.size]
                )
            }
        }
    }
}

@Composable
private fun CategoryDonutChart(
    rows: List<CategoryTotal>,
    totalMinor: Long,
    currency: String
) {
    val safeTotal = totalMinor.coerceAtLeast(1)
    Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(108.dp)) {
            val stroke = 17.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            rows.forEachIndexed { index, row ->
                val sweep = 360f * row.amountMinor / safeTotal
                drawArc(
                    color = LedgerChartColors[index % LedgerChartColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatCompactMoney(totalMinor, "").trim().ifBlank { "0" },
                style = MaterialTheme.typography.titleMedium,
                color = Ledger.Ink
            )
            Text(currency, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        }
    }
}

@Composable
private fun CategoryLegendRow(
    row: CategoryTotal,
    totalMinor: Long,
    color: Color
) {
    val percent = if (totalMinor == 0L) 0 else ((row.amountMinor * 100.0) / totalMinor).roundToInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            row.category.displayName,
            modifier = Modifier.weight(1f).padding(start = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Ledger.Ink
        )
        Text("$percent%", style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
    }
}

@Composable
fun MonthlyBarChart(
    months: List<MonthlyTotal>,
    modifier: Modifier = Modifier
) {
    if (months.isEmpty()) return
    val maxBarHeight = 96.dp
    val maxAmount = months.maxOf { it.amountMinor }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(maxBarHeight),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            months.forEach { month ->
                val fraction = (month.amountMinor.toFloat() / maxAmount).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 3.dp)
                            .height((maxBarHeight * fraction).coerceAtLeast(2.dp))
                            .background(
                                color = if (month.amountMinor > 0) Ledger.Primary else Ledger.Outline,
                                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            months.forEach { month ->
                Text(
                    month.month.format(MONTH_LABEL_FORMAT),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
