package com.example.smscosttracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.SpendingAnalysis
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.components.CategoryChart
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.MonthlyBarChart
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TREND_MONTHS = 6
private val TREND_MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

@Composable
fun InsightsScreen(
    repository: TransactionRepository,
    refreshKey: Int
) {
    val state = rememberTransactions(repository, refreshKey)
    val currencies = remember(state.transactions) {
        state.transactions.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var currency by remember(currencies) { mutableStateOf(currencies.firstOrNull() ?: "SAR") }
    val month = YearMonth.now()
    val filter = TransactionFilterState(
        preset = DatePreset.THIS_MONTH,
        currency = currency
    )
    val rows = remember(state.transactions, filter) {
        TransactionFilters.apply(state.transactions, filter)
    }
    val summary = remember(rows) { SpendingAnalysis.summarize(rows, topMerchantCount = 5) }
    val allTimeRows = remember(state.transactions, currency) {
        TransactionFilters.apply(
            state.transactions,
            TransactionFilterState(preset = DatePreset.ALL, currency = currency)
        )
    }
    val allTimeSummary = remember(allTimeRows) {
        SpendingAnalysis.summarize(allTimeRows, topMerchantCount = 5)
    }
    val showingAllTime = rows.isEmpty()
    val displaySummary = if (showingAllTime) allTimeSummary else summary
    val displayRows = if (showingAllTime) allTimeRows else rows
    val categoryRows = remember(displaySummary.byCategory) {
        displaySummary.byCategory.sortedByDescending { it.amountMinor }
    }
    val trendAnchor = if (showingAllTime) {
        allTimeSummary.byMonth.lastOrNull()?.month ?: YearMonth.now()
    } else {
        YearMonth.now()
    }
    val trendMonths = remember(allTimeRows, trendAnchor) {
        SpendingAnalysis.monthlyTrend(allTimeRows, months = TREND_MONTHS, now = trendAnchor)
    }
    val trendLabel = remember(trendMonths) {
        if (trendMonths.isEmpty()) {
            "No activity"
        } else {
            "${trendMonths.first().month.format(TREND_MONTH_FORMAT)} – " +
                trendMonths.last().month.format(TREND_MONTH_FORMAT)
        }
    }
    val cards = displayRows.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.CARD }
        .sumOf { it.amountMinor }
    val accounts = displayRows.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.ACCOUNT }
        .sumOf { it.amountMinor }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    val headerLabel = if (showingAllTime && displayRows.isNotEmpty()) "All time" else monthLabel

    ScreenBackground {
        if (state.loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Ledger.Primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                PageHeader("Insights", headerLabel)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    currencies.forEach { code ->
                        LedgerChip(
                            text = code,
                            selected = code == currency,
                            onClick = { currency = code }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (displayRows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Insights,
                        title = "No insights yet",
                        body = "Import SMS or choose a month with transactions."
                    )
                } else {
                    LedgerCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                if (showingAllTime) "Spent all time" else "Spent this month",
                                style = MaterialTheme.typography.labelMedium,
                                color = Ledger.Muted
                            )
                            Text(
                                formatMoney(displaySummary.totalMinor, currency),
                                style = MaterialTheme.typography.displayMedium,
                                color = Ledger.Ink,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Text(
                                "${displayRows.size} transactions · ${displaySummary.topMerchants.size} top merchants",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ledger.Muted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                InsightStat("Cards", formatMoney(cards, currency), Modifier.weight(1f))
                                InsightStat("Accounts", formatMoney(accounts, currency), Modifier.weight(1f))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("By category", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    Spacer(Modifier.height(8.dp))
                    LedgerCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Category mix",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Ledger.Ink
                                )
                                Text(
                                    formatMoney(displaySummary.totalMinor, currency),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Ledger.Muted
                                )
                            }
                            CategoryChart(
                                rows = categoryRows,
                                totalMinor = displaySummary.totalMinor,
                                currency = currency,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Monthly trend", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    Spacer(Modifier.height(8.dp))
                    LedgerCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "$trendLabel · $currency",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ledger.Muted
                            )
                            Spacer(Modifier.height(10.dp))
                            MonthlyBarChart(months = trendMonths)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Top merchants", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    Spacer(Modifier.height(8.dp))
                    LedgerCard {
                        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            displaySummary.topMerchants.forEach { merchant ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        merchant.merchant,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ledger.Ink
                                    )
                                    Text(
                                        formatMoney(merchant.amountMinor, currency),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Ledger.Muted
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun InsightStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}
