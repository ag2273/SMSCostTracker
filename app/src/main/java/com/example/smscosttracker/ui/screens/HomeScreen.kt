package com.example.smscosttracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.example.smscosttracker.ui.components.ChannelIcon
import com.example.smscosttracker.ui.components.DropdownChip
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ReviewBanner
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SectionRow
import com.example.smscosttracker.ui.components.channelLabel
import com.example.smscosttracker.ui.components.formatCompactMoney
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    onOpenInstruments: () -> Unit,
    onOpenActivity: () -> Unit,
    onImport: () -> Unit
) {
    val state = rememberTransactions(repository, refreshKey)
    val pending = rememberPendingCount(repository, refreshKey)
    val currencies = remember(state.transactions) {
        state.transactions.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val accounts = remember(state.transactions) {
        state.transactions.map { it.account }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var currency by remember(currencies) { mutableStateOf(currencies.firstOrNull() ?: "SAR") }
    var account by remember { mutableStateOf<String?>(null) }
    val month = YearMonth.now()
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

    val filter = TransactionFilterState(
        preset = DatePreset.THIS_MONTH,
        currency = currency,
        account = account
    )
    val monthTransactions = remember(state.transactions, filter) {
        TransactionFilters.apply(state.transactions, filter)
    }
    val summary = remember(monthTransactions) {
        SpendingAnalysis.summarize(monthTransactions, topMerchantCount = 4)
    }
    val cards = monthTransactions.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.CARD }
        .sumOf { it.amountMinor }
    val bankAccounts = monthTransactions.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.ACCOUNT }
        .sumOf { it.amountMinor }
    val recent = remember(monthTransactions) {
        monthTransactions.sortedByDescending { it.occurredAt }.take(3)
    }
    val categoryRows = remember(summary.byCategory) {
        val sorted = summary.byCategory.sortedByDescending { it.amountMinor }
        if (sorted.size <= 4) {
            sorted
        } else {
            sorted.take(3) + CategoryTotal(
                Category.OTHER,
                sorted.drop(3).sumOf { it.amountMinor }
            )
        }
    }

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
                PageHeader(
                    title = "Home",
                    subtitle = monthLabel,
                    trailing = {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Ledger.Primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("A", color = Ledger.OnPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currencies.forEach { code ->
                        LedgerChip(
                            text = code,
                            selected = code == currency,
                            onClick = { currency = code }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    DropdownChip(
                        label = account ?: "All accounts",
                        options = listOf("All accounts") + accounts,
                        onSelect = { selected ->
                            account = selected.takeUnless { it == "All accounts" }
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                SpendHero(
                    totalMinor = summary.totalMinor,
                    currency = currency,
                    transactionCount = monthTransactions.size,
                    cardsMinor = cards,
                    accountsMinor = bankAccounts
                )
                if (pending > 0) {
                    ReviewBanner(
                        title = "$pending card/account values need review",
                        body = "Confirm them to improve this month's totals.",
                        onClick = onOpenInstruments,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionRow(title = "Top categories", action = "See all", onAction = onOpenActivity)
                        if (categoryRows.isEmpty()) {
                            Text(
                                "No spending yet this month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Ledger.Muted,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CategoryDonut(
                                    rows = categoryRows,
                                    totalMinor = summary.totalMinor,
                                    currency = currency
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    categoryRows.forEachIndexed { index, row ->
                                        CategoryLegendRow(
                                            row = row,
                                            totalMinor = summary.totalMinor,
                                            color = LedgerChartColors[index % LedgerChartColors.size]
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionRow(title = "Recent", action = "See all", onAction = onOpenActivity)
                if (recent.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "No transactions yet",
                        body = "Import your bank SMS to see this month's spending."
                    )
                    PrimaryButton("Import SMS", onImport)
                } else {
                    LedgerCard {
                        Column {
                            recent.forEachIndexed { index, transaction ->
                                RecentRow(transaction = transaction, onClick = onOpenActivity)
                                if (index != recent.lastIndex) {
                                    LedgerDivider(modifier = Modifier.padding(start = 60.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SpendHero(
    totalMinor: Long,
    currency: String,
    transactionCount: Int,
    cardsMinor: Long,
    accountsMinor: Long
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Ledger.Primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                "SPENT THIS MONTH",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.OnPrimary.copy(alpha = 0.75f)
            )
            Text(
                formatMoney(totalMinor, currency),
                style = MaterialTheme.typography.displayMedium,
                color = Ledger.OnPrimary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                "$transactionCount transactions",
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.OnPrimary.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 13.dp)
                    .background(Ledger.OnPrimary.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Cards",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.OnPrimary.copy(alpha = 0.72f)
                    )
                    Text(
                        formatMoney(cardsMinor, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.OnPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Accounts",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.OnPrimary.copy(alpha = 0.72f)
                    )
                    Text(
                        formatMoney(accountsMinor, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.OnPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryDonut(
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
                formatCompactMoney(totalMinor, "").trim().removePrefix("?").ifBlank { "0" },
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
    color: androidx.compose.ui.graphics.Color
) {
    val pct = if (totalMinor == 0L) 0 else ((row.amountMinor * 100.0) / totalMinor).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(
            row.category.displayName,
            modifier = Modifier.weight(1f).padding(start = 7.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Ledger.Ink
        )
        Text(
            "$pct%",
            style = MaterialTheme.typography.labelSmall,
            color = Ledger.Muted
        )
    }
}

@Composable
private fun RecentRow(transaction: TransactionEntity, onClick: () -> Unit) {
    val channel = TransactionChannel.fromStorage(transaction.channel)
    val date = Instant.ofEpochMilli(transaction.occurredAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val dateLabel = when (date) {
        java.time.LocalDate.now() -> "Today"
        java.time.LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Avatar(
            letter = transaction.merchant,
            color = LedgerChartColors[(transaction.merchant.hashCode() and Int.MAX_VALUE) % LedgerChartColors.size]
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                transaction.merchant,
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    Category.fromStorage(transaction.category).displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted
                )
                Text("·", style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                ChannelIcon(channel)
                Text(
                    channelLabel(channel),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatMoney(transaction.amountMinor, transaction.currency),
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink
            )
            Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        }
    }
}
