package com.example.smscosttracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.HomeFilterState
import com.example.smscosttracker.analysis.HomePeriodUnit
import com.example.smscosttracker.analysis.SpendingAnalysis
import com.example.smscosttracker.analysis.TransactionReview
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.CategoryChart
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
import com.example.smscosttracker.ui.components.formatExpenseMoney
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    filter: HomeFilterState,
    onFilterChange: (HomeFilterState) -> Unit,
    onOpenInstruments: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenReview: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onImport: () -> Unit
) {
    val state = rememberTransactions(repository, refreshKey)
    val recurring = rememberRecurringSeries(repository, refreshKey)
    val pending = rememberPendingCount(repository, refreshKey)
    val pendingKeys = rememberPendingInstrumentKeys(repository, refreshKey)
    val reviewCount = remember(state.transactions, pendingKeys) {
        state.transactions.count { TransactionReview.needsReview(it, pendingKeys) }
    }
    val currencies = remember(state.transactions) {
        state.transactions.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val accounts = remember(state.transactions) {
        state.transactions.map { it.account }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val currency = filter.currency?.takeIf { it in currencies }
        ?: currencies.firstOrNull()
        ?: "SAR"

    LaunchedEffect(currencies) {
        if (!filter.currencyInitialized && currencies.isNotEmpty()) {
            onFilterChange(
                filter.copy(
                    currency = filter.currency?.takeIf { it in currencies } ?: currencies.first(),
                    currencyInitialized = true
                )
            )
        }
    }

    val rangeFilter = TransactionFilterState(
        preset = DatePreset.CUSTOM,
        currency = currency,
        account = filter.account,
        customStart = filter.periodStart,
        customEnd = filter.periodEndExclusive.minusDays(1)
    )
    val periodTransactions = remember(state.transactions, rangeFilter) {
        TransactionFilters.apply(state.transactions, rangeFilter, linkedAccounts = state.linkedAccounts)
    }
    val summary = remember(periodTransactions) {
        SpendingAnalysis.summarize(periodTransactions, topMerchantCount = 4)
    }
    val cards = periodTransactions.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.CARD }
        .sumOf { it.amountMinor }
    val bankAccounts = periodTransactions.filter { TransactionChannel.fromStorage(it.channel) == TransactionChannel.ACCOUNT }
        .sumOf { it.amountMinor }
    val recent = remember(periodTransactions) {
        periodTransactions.sortedByDescending { it.occurredAt }.take(3)
    }
    val categoryRows = remember(summary.byCategory) {
        SpendingAnalysis.collapseIntoOther(summary.byCategory, maxRows = 4)
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
                    subtitle = filter.periodLabel(),
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
                Spacer(Modifier.height(10.dp))
                PeriodPicker(
                    filter = filter,
                    onFilterChange = onFilterChange
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Scrolls so many currencies never squeeze the account picker.
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        currencies.forEach { code ->
                            LedgerChip(
                                text = code,
                                selected = code == currency,
                                onClick = {
                                    onFilterChange(filter.copy(currency = code, currencyInitialized = true))
                                }
                            )
                        }
                    }
                    DropdownChip(
                        label = filter.account ?: "All accounts",
                        options = listOf("All accounts") + accounts,
                        onSelect = { selected ->
                            onFilterChange(
                                filter.copy(account = selected.takeUnless { it == "All accounts" })
                            )
                        }
                    )
                }
                Spacer(Modifier.height(14.dp))
                SpendHero(
                    label = filter.spendLabel(),
                    totalMinor = summary.totalMinor,
                    currency = currency,
                    transactionCount = periodTransactions.size,
                    cardsMinor = cards,
                    accountsMinor = bankAccounts
                )
                if (pending > 0) {
                    ReviewBanner(
                        title = "$pending card/account values need review",
                        body = "Confirm them to improve this period's totals.",
                        onClick = onOpenInstruments,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (reviewCount > 0) {
                    ReviewBanner(
                        title = "$reviewCount transactions need review",
                        body = "Low-confidence categories, unknown merchants or unconfirmed cards.",
                        onClick = onOpenReview,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                SubscriptionsCard(
                    series = recurring.series,
                    currency = currency,
                    onClick = onOpenSubscriptions,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(Modifier.height(16.dp))
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionRow(title = "Top categories", action = "See all", onAction = onOpenActivity)
                        CategoryChart(
                            rows = categoryRows,
                            totalMinor = summary.totalMinor,
                            currency = currency,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionRow(title = "Recent", action = "See all", onAction = onOpenActivity)
                if (recent.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.ReceiptLong,
                        title = "No transactions yet",
                        body = "Import your bank SMS to see spending for this period."
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
private fun PeriodPicker(
    filter: HomeFilterState,
    onFilterChange: (HomeFilterState) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        LedgerChip(
            text = "Month",
            selected = filter.unit == HomePeriodUnit.MONTH,
            onClick = { onFilterChange(filter.copy(unit = HomePeriodUnit.MONTH)) }
        )
        LedgerChip(
            text = "Quarter",
            selected = filter.unit == HomePeriodUnit.QUARTER,
            onClick = { onFilterChange(filter.copy(unit = HomePeriodUnit.QUARTER)) }
        )
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = { onFilterChange(filter.shiftPeriod(-1)) },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = "Previous period",
                tint = Ledger.Ink,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(
            onClick = { onFilterChange(filter.shiftPeriod(1)) },
            enabled = filter.canMoveForward(),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Next period",
                tint = if (filter.canMoveForward()) Ledger.Ink else Ledger.Outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SpendHero(
    label: String,
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
                label,
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
private fun RecentRow(transaction: TransactionEntity, onClick: () -> Unit) {
    val channel = TransactionChannel.fromStorage(transaction.channel)
    val digits = transaction.account.substringAfterLast(' ').trim().takeIf { it.isNotBlank() }
    val instrument = listOfNotNull(channelLabel(channel), digits).joinToString(" ")
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
            color = LedgerChartColors[(transaction.merchant.hashCode() and Int.MAX_VALUE) % LedgerChartColors.size],
            size = 38
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                transaction.merchant,
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    Category.fromStorage(transaction.category).displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Ink.copy(alpha = 0.85f)
                )
                Text("·", style = MaterialTheme.typography.labelMedium, color = Ledger.Muted)
                ChannelIcon(channel, size = 14)
                Text(
                    instrument,
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Ink
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatExpenseMoney(transaction.amountMinor, transaction.currency),
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink
            )
            Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        }
    }
}
