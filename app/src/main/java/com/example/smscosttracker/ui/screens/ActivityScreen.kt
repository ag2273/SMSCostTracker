package com.example.smscosttracker.ui.screens

import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.ActivityFilterState
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.parsing.TransactionParser
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.ChannelIcon
import com.example.smscosttracker.ui.components.DropdownChip
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerTextField
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.channelLabel
import com.example.smscosttracker.ui.components.formatExpenseMoney
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.components.rememberCsvExport
import com.example.smscosttracker.ui.sheets.CategorySheet
import com.example.smscosttracker.ui.sheets.FiltersSheet
import com.example.smscosttracker.ui.sheets.TransactionDetailSheet
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

@Composable
fun ActivityScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    filters: ActivityFilterState,
    onFiltersChange: (ActivityFilterState) -> Unit,
    onDataChanged: () -> Unit
) {
    val state = rememberTransactions(repository, refreshKey)
    val context = LocalContext.current
    val exportCsv = rememberCsvExport { message ->
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val currencies = remember(state.transactions) {
        state.transactions.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val accounts = remember(state.transactions) {
        state.transactions.map { it.account }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val filter = filters.filter
    var pendingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showFilters by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TransactionEntity?>(null) }
    var categoryFor by remember { mutableStateOf<TransactionEntity?>(null) }

    fun updateFilter(next: TransactionFilterState) {
        onFiltersChange(filters.copy(filter = next, currencyInitialized = true))
    }

    LaunchedEffect(currencies, filters.currencyInitialized) {
        if (!filters.currencyInitialized && currencies.isNotEmpty()) {
            onFiltersChange(
                filters.copy(
                    filter = filter.copy(
                        currency = filter.currency?.takeIf { it in currencies } ?: currencies.first()
                    ),
                    currencyInitialized = true
                )
            )
        }
    }
    LaunchedEffect(refreshKey) {
        thread {
            val keys = repository.pendingInstrumentKeys()
            Handler(Looper.getMainLooper()).post { pendingKeys = keys }
        }
    }

    val filtered = remember(state.transactions, filter) {
        TransactionFilters.apply(state.transactions, filter)
    }
    val grouped = remember(filtered) {
        filtered
            .sortedByDescending { it.occurredAt }
            .groupBy {
                Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
    }
    val activeFilters = countFilters(filter, currencies.firstOrNull())

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            PageHeader(
                "Activity",
                "${filtered.size} transactions · ${TransactionFilters.labelFor(filter)}",
                trailing = {
                    IconButton(onClick = { exportCsv(filtered) }) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = "Export CSV",
                            tint = Ledger.Ink
                        )
                    }
                }
            )
            Spacer(Modifier.height(14.dp))
            LedgerTextField(
                value = filter.merchantQuery,
                onValueChange = { updateFilter(filter.copy(merchantQuery = it)) },
                placeholder = "Search merchant or amount",
                leadingIcon = Icons.Outlined.Search
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                LedgerChip(
                    text = TransactionFilters.labelFor(filter),
                    selected = true,
                    onClick = { showFilters = true }
                )
                LedgerChip(
                    text = if (activeFilters == 0) "Filters" else "$activeFilters filters",
                    selected = activeFilters > 0,
                    onClick = { showFilters = true }
                )
                Spacer(Modifier.weight(1f))
                DropdownChip(
                    label = filter.currency ?: "All currencies",
                    options = listOf("All currencies") + currencies,
                    onSelect = { selected ->
                        updateFilter(
                            filter.copy(
                                currency = selected.takeUnless { it == "All currencies" }
                            )
                        )
                    }
                )
                if (activeFilters > 0) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = Ledger.Primary,
                        modifier = Modifier.clickable {
                            updateFilter(
                                TransactionFilterState(
                                    preset = DatePreset.THIS_MONTH,
                                    currency = currencies.firstOrNull()
                                )
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (filtered.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Search,
                    title = "No transactions match",
                    body = "Try a different month, category or search term.",
                    modifier = Modifier.padding(top = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    grouped.forEach { (date, rows) ->
                        item(key = "group-${date}") {
                            Column {
                                DateGroupHeader(date = date, rows = rows)
                                LedgerCard {
                                    Column {
                                        rows.forEachIndexed { index, transaction ->
                                            ActivityRow(
                                                transaction = transaction,
                                                isPending = transaction.isPending(pendingKeys),
                                                onClick = { detail = transaction }
                                            )
                                            if (index != rows.lastIndex) {
                                                LedgerDivider(modifier = Modifier.padding(start = 62.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            filter = filter,
            accounts = accounts,
            currencies = currencies,
            allTransactions = state.transactions,
            onDismiss = { showFilters = false },
            onApply = {
                updateFilter(it)
                showFilters = false
            }
        )
    }

    detail?.let { transaction ->
        TransactionDetailSheet(
            transaction = transaction,
            repository = repository,
            onDismiss = { detail = null },
            onCategory = {
                detail = null
                categoryFor = transaction
            },
            onChanged = {
                detail = null
                onDataChanged()
            }
        )
    }

    categoryFor?.let { transaction ->
        CategorySheet(
            transaction = transaction,
            onDismiss = { categoryFor = null },
            onSave = { category ->
                thread {
                    repository.updateCategory(transaction, category)
                    Handler(Looper.getMainLooper()).post {
                        categoryFor = null
                        onDataChanged()
                    }
                }
            }
        )
    }
}

private fun countFilters(filter: TransactionFilterState, firstCurrency: String?): Int {
    var count = 0
    if (filter.preset != DatePreset.THIS_MONTH) count++
    if (filter.category != null) count++
    if (filter.account != null) count++
    if (filter.currency != null && filter.currency != firstCurrency) count++
    if (filter.merchantQuery.isNotBlank()) count++
    return count
}

private fun TransactionEntity.isPending(pendingKeys: Set<String>): Boolean {
    val digits = account.substringAfterLast(' ').trim()
    if (digits.isEmpty()) return false
    return TransactionParser.instrumentKey(sourceAddress, digits) in pendingKeys
}

@Composable
private fun DateGroupHeader(date: LocalDate, rows: List<TransactionEntity>) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today · ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
        today.minusDays(1) -> "Yesterday · ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))}"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)
    }
    val currencies = rows.map { it.currency }.distinct()
    val total = if (currencies.size == 1) {
        "-" + formatMoney(rows.sumOf { it.amountMinor }, currencies.first())
    } else {
        "${currencies.size} currencies"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = Ledger.Muted
        )
        Text(total, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
    }
}

@Composable
private fun ActivityRow(
    transaction: TransactionEntity,
    isPending: Boolean,
    onClick: () -> Unit
) {
    val channel = TransactionChannel.fromStorage(transaction.channel)
    val digits = transaction.account.substringAfterLast(' ').trim().takeIf { it.isNotBlank() }
    val instrument = listOfNotNull(channelLabel(channel), digits).joinToString(" ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
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
                if (isPending) {
                    Pill("Review", PillTone.Accent)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatExpenseMoney(transaction.amountMinor, transaction.currency),
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink
            )
            Text(
                Instant.ofEpochMilli(transaction.occurredAt)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)),
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
        }
    }
}
