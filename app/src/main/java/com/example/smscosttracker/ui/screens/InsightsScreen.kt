package com.example.smscosttracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.AccountCurrencies
import com.example.smscosttracker.analysis.CategoryTotal
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.SpendingAnalysis
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.CategoryChart
import com.example.smscosttracker.ui.components.ChannelIcon
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.MonthlyBarChart
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.channelLabel
import com.example.smscosttracker.ui.components.formatExpenseMoney
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val TREND_MONTHS = 6
private val TREND_MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

private data class MerchantMeta(
    val visits: Int,
    val category: Category,
    val channel: TransactionChannel,
    val digits: String?
)

@Composable
fun InsightsScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    onOpenSubscriptions: () -> Unit
) {
    val state = rememberTransactions(repository, refreshKey)
    val recurring = rememberRecurringSeries(repository, refreshKey)
    val currencies = remember(state.transactions) {
        AccountCurrencies.byUse(state.transactions)
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
    val merchantMeta = remember(displayRows) { displayRows.merchantMetadata() }
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
                CurrencySegmented(
                    currencies = currencies,
                    selected = currency,
                    onSelect = { currency = it }
                )
                SubscriptionsCard(
                    series = recurring.series,
                    currency = currency,
                    onClick = onOpenSubscriptions,
                    modifier = Modifier.padding(top = 14.dp)
                )
                Spacer(Modifier.height(14.dp))
                if (displayRows.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Insights,
                        title = "No insights yet",
                        body = "Import SMS or choose a month with transactions."
                    )
                } else {
                    CategoryCard(
                        rows = categoryRows,
                        totalMinor = displaySummary.totalMinor,
                        currency = currency
                    )
                    Spacer(Modifier.height(14.dp))
                    CardsVsAccountsCard(cards = cards, accounts = accounts, currency = currency)
                    Spacer(Modifier.height(14.dp))
                    LedgerCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Monthly trend",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Ledger.Ink
                                )
                                Text(
                                    trendLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ledger.Muted
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            MonthlyBarChart(months = trendMonths)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    TopMerchantsCard(
                        merchants = displaySummary.topMerchants.map {
                            it to merchantMeta[it.merchant]
                        },
                        currency = currency
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryCard(
    rows: List<CategoryTotal>,
    totalMinor: Long,
    currency: String
) {
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "By category",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ledger.Ink
                )
                Text(
                    formatMoney(totalMinor, currency),
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Muted
                )
            }
            CategoryChart(
                rows = rows,
                totalMinor = totalMinor,
                currency = currency,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun CardsVsAccountsCard(cards: Long, accounts: Long, currency: String) {
    val total = cards + accounts
    val cardShare = if (total > 0) ((cards * 100.0) / total).roundToInt() else 0
    LedgerCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cards vs Accounts",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ledger.Ink
                )
                Text(
                    "$cardShare% cards",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Muted
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(99.dp))
            ) {
                if (cards > 0) {
                    Box(
                        Modifier
                            .weight(cards.toFloat())
                            .fillMaxHeight()
                            .background(Ledger.Primary)
                    )
                }
                if (accounts > 0) {
                    Box(
                        Modifier
                            .weight(accounts.toFloat())
                            .fillMaxHeight()
                            .background(Ledger.Accent)
                    )
                }
                if (total == 0L) {
                    Box(Modifier.fillMaxSize().background(Ledger.SurfaceAlt))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        formatMoney(cards, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.Ink
                    )
                    Text("Cards", style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        formatMoney(accounts, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.Ink
                    )
                    Text("Accounts", style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
                }
            }
        }
    }
}

@Composable
private fun TopMerchantsCard(
    merchants: List<Pair<com.example.smscosttracker.analysis.MerchantTotal, MerchantMeta?>>,
    currency: String
) {
    LedgerCard {
        Column {
            Text(
                "Top merchants",
                style = MaterialTheme.typography.titleMedium,
                color = Ledger.Ink,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            merchants.forEachIndexed { index, (merchant, meta) ->
                MerchantRow(merchant.merchant, merchant.amountMinor, meta, currency)
                if (index != merchants.lastIndex) {
                    LedgerDivider(modifier = Modifier.padding(start = 65.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MerchantRow(
    merchant: String,
    amountMinor: Long,
    meta: MerchantMeta?,
    currency: String
) {
    val channel = meta?.channel ?: TransactionChannel.OTHER
    val instrument = meta?.digits?.let { "${channelLabel(channel)} $it" } ?: channelLabel(channel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Avatar(
            letter = merchant,
            color = LedgerChartColors[(merchant.hashCode() and Int.MAX_VALUE) % LedgerChartColors.size],
            size = 38
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                merchant,
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    meta?.category?.displayName ?: Category.OTHER.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Ink.copy(alpha = 0.85f)
                )
                Text("·", style = MaterialTheme.typography.labelMedium, color = Ledger.Muted)
                ChannelIcon(channel, size = 14)
                Text(instrument, style = MaterialTheme.typography.labelMedium, color = Ledger.Ink)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatExpenseMoney(amountMinor, currency),
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink
            )
            Text(
                "${meta?.visits ?: 0} visits",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
        }
    }
}

@Composable
private fun CurrencySegmented(
    currencies: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // One currency, for example with "Show amounts in" set, needs no switch.
    if (currencies.size <= 1) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Ledger.SurfaceAlt
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            currencies.forEach { code ->
                val active = code == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(code) },
                    shape = RoundedCornerShape(11.dp),
                    color = if (active) Ledger.Surface else Color.Transparent,
                    border = if (active) BorderStroke(1.dp, Ledger.Outline) else null
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            code,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) Ledger.Ink else Ledger.Muted
                        )
                    }
                }
            }
        }
    }
}

private fun List<TransactionEntity>.merchantMetadata(): Map<String, MerchantMeta> =
    groupBy { it.merchant }.mapValues { (_, transactions) ->
        val sample = transactions.maxByOrNull { it.occurredAt } ?: transactions.first()
        MerchantMeta(
            visits = transactions.size,
            category = Category.fromStorage(sample.category),
            channel = TransactionChannel.fromStorage(sample.channel),
            digits = sample.account.substringAfterLast(' ').trim().takeIf { it.isNotBlank() }
        )
    }
