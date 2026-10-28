package com.example.smscosttracker.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.AmountPattern
import com.example.smscosttracker.analysis.RecurringCycle
import com.example.smscosttracker.analysis.RecurringSeries
import com.example.smscosttracker.analysis.SubscriptionPill
import com.example.smscosttracker.analysis.SubscriptionTotal
import com.example.smscosttracker.analysis.Subscriptions
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.IconTile
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerTextField
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ReviewBanner
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SecondaryButton
import com.example.smscosttracker.ui.components.SectionRow
import com.example.smscosttracker.ui.components.SheetHeader
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import com.example.smscosttracker.ui.theme.LedgerChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

private val ROW_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

@Composable
fun SubscriptionsScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    initialSeriesKey: String? = null
) {
    val state = rememberRecurringSeries(repository, refreshKey)
    var selectedKey by remember(initialSeriesKey) { mutableStateOf(initialSeriesKey) }
    val today = LocalDate.now()
    val current = remember(state.series) { Subscriptions.current(state.series) }
    val ended = remember(state.series, today) { Subscriptions.ended(state.series, today) }
    val totals = remember(state.series) { Subscriptions.monthlyTotals(state.series) }
    val dueSoon = remember(state.series, today) { Subscriptions.dueSoon(state.series, today).size }
    val colorByKey = remember(state.series) {
        state.series.sortedBy { it.key }.mapIndexed { index, series ->
            series.key to LedgerChartColors[index % LedgerChartColors.size]
        }.toMap()
    }

    fun act(action: () -> Unit) {
        selectedKey = null
        thread {
            action()
            Handler(Looper.getMainLooper()).post { onChanged() }
        }
    }

    state.series.firstOrNull { it.key == selectedKey }?.let { series ->
        SubscriptionDetailSheet(
            series = series,
            payments = series.transactionIds.mapNotNull { state.transactionsById[it] },
            onClose = { selectedKey = null },
            onConfirm = { act { repository.confirmRecurring(series) } },
            onRename = { name -> act { repository.renameRecurring(series, name) } },
            onSetCycle = { cycle -> act { repository.setRecurringCycle(series, cycle) } },
            onDismissSeries = { act { repository.dismissRecurring(series) } }
        )
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
                    title = "Subscriptions",
                    subtitle = "Payments that repeat, found in your SMS",
                    onBack = onBack
                )
                Spacer(Modifier.height(14.dp))
                if (current.isEmpty() && ended.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Autorenew,
                        title = "No subscriptions found yet",
                        body = "Payments that repeat weekly, monthly, quarterly or yearly show up here " +
                            "after three payments, or two for yearly ones.",
                        modifier = Modifier.testTag("subscriptions-empty")
                    )
                } else {
                    if (totals.isNotEmpty()) {
                        MonthlyHero(totals = totals, activeCount = current.size, dueSoon = dueSoon)
                        Spacer(Modifier.height(16.dp))
                    }
                    if (current.isNotEmpty()) {
                        SectionRow(title = "Active", action = null)
                        Spacer(Modifier.height(6.dp))
                        SeriesList(current, colorByKey) { selectedKey = it.key }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (ended.isNotEmpty()) {
                        SectionRow(title = "Ended", action = null)
                        Spacer(Modifier.height(6.dp))
                        SeriesList(ended, colorByKey) { selectedKey = it.key }
                        Spacer(Modifier.height(16.dp))
                    }
                    ReviewBanner(
                        title = "Not a subscription?",
                        body = "Open it and choose Dismiss. It won't be suggested again."
                    )
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

/** The Home and Insights entry point: monthly cost in one currency and how many payments are due soon. */
@Composable
fun SubscriptionsCard(
    series: List<RecurringSeries>,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totals = remember(series) { Subscriptions.monthlyTotals(series) }
    val dueSoon = remember(series) { Subscriptions.dueSoon(series, LocalDate.now()).size }
    if (totals.isEmpty()) return
    val total = totals.firstOrNull { it.currency == currency } ?: totals.first()
    val details = buildList {
        add("${formatMoney(total.monthlyMinor, total.currency)}/month")
        if (dueSoon > 0) add("$dueSoon due this week")
        if (totals.size == 2) add("+1 more currency")
        if (totals.size > 2) add("+${totals.size - 1} more currencies")
    }
    LedgerCard(modifier = modifier.clickable(onClick = onClick).testTag("subscriptions-card")) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconTile(Icons.Outlined.Autorenew)
            Column(modifier = Modifier.weight(1f)) {
                Text("Subscriptions", style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Ledger.Muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MonthlyHero(totals: List<SubscriptionTotal>, activeCount: Int, dueSoon: Int) {
    val main = totals.first()
    val others = totals.drop(1)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Ledger.Primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                "EVERY MONTH",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.OnPrimary.copy(alpha = 0.78f)
            )
            Text(
                formatMoney(main.monthlyMinor, main.currency),
                style = MaterialTheme.typography.displayMedium,
                color = Ledger.OnPrimary,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                listOfNotNull(
                    "$activeCount active",
                    "$dueSoon due this week".takeIf { dueSoon > 0 },
                    "yearly payments counted per month"
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.OnPrimary.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 2.dp)
            )
            others.chunked(2).forEach { pair ->
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ledger.OnPrimary.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    pair.forEach { total ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                total.currency,
                                style = MaterialTheme.typography.labelSmall,
                                color = Ledger.OnPrimary.copy(alpha = 0.72f)
                            )
                            Text(
                                "${formatMoney(total.monthlyMinor, total.currency)} / month",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ledger.OnPrimary
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SeriesList(
    series: List<RecurringSeries>,
    colorByKey: Map<String, androidx.compose.ui.graphics.Color>,
    onOpen: (RecurringSeries) -> Unit
) {
    LedgerCard {
        Column {
            series.forEachIndexed { index, item ->
                SeriesRow(item, colorByKey[item.key] ?: Ledger.Primary) { onOpen(item) }
                if (index != series.lastIndex) LedgerDivider(modifier = Modifier.padding(start = 60.dp))
            }
        }
    }
}

@Composable
private fun SeriesRow(series: RecurringSeries, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val pill = Subscriptions.pill(series)
    val dateText = if (pill == SubscriptionPill.ENDED) {
        "last ${series.lastDate.format(ROW_DATE)}"
    } else {
        "next ${series.nextExpected.format(ROW_DATE)}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Avatar(letter = series.merchant, color = color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                series.merchant,
                style = MaterialTheme.typography.titleSmall,
                color = Ledger.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    "${series.cycle.label} · $dateText",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ledger.Muted,
                    maxLines = 1
                )
                StatusPill(pill)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amountText(series), style = MaterialTheme.typography.titleSmall, color = Ledger.Ink, maxLines = 1)
            secondaryAmountText(series, pill)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatusPill(pill: SubscriptionPill) {
    val tone = when (pill) {
        SubscriptionPill.ACTIVE -> PillTone.Success
        SubscriptionPill.LATE -> PillTone.Danger
        SubscriptionPill.ENDED -> PillTone.Neutral
        SubscriptionPill.PRICE_CHANGED -> PillTone.Accent
    }
    Pill(text = pill.label, tone = tone)
}

private fun amountText(series: RecurringSeries): String {
    val amount = formatMoney(series.typicalMinor, series.currency)
    return if (series.amountPattern == AmountPattern.VARIABLE) "≈ $amount" else amount
}

private fun secondaryAmountText(series: RecurringSeries, pill: SubscriptionPill): String? = when {
    pill == SubscriptionPill.PRICE_CHANGED ->
        series.priceChange?.let { "was ${formatMoney(it.fromMinor, series.currency)}" }
    series.amountPattern == AmountPattern.VARIABLE ->
        "${formatMoney(series.minMinor, series.currency)}–${plainAmount(series.maxMinor)}"
    series.cycle == RecurringCycle.YEARLY || series.cycle == RecurringCycle.QUARTERLY ->
        "${formatMoney(Subscriptions.monthlyMinor(series), series.currency)} / month"
    pill == SubscriptionPill.LATE -> "last ${series.lastDate.format(ROW_DATE)}"
    else -> null
}

private fun plainAmount(minor: Long): String = formatMoney(minor, "").substringAfter(' ')

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubscriptionDetailSheet(
    series: RecurringSeries,
    payments: List<TransactionEntity>,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    onRename: (String) -> Unit,
    onSetCycle: (RecurringCycle) -> Unit,
    onDismissSeries: () -> Unit
) {
    var renaming by remember(series.key) { mutableStateOf(false) }
    var name by remember(series.key) { mutableStateOf(series.merchant) }
    val pill = Subscriptions.pill(series)
    val zone = ZoneId.systemDefault()
    val history = remember(payments) { payments.sortedByDescending { it.occurredAt } }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Ledger.Background,
        tonalElevation = 0.dp,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetHeader(
                title = series.merchant,
                subtitle = listOfNotNull(series.cycle.label, series.account.ifBlank { null }).joinToString(" · "),
                onClose = onClose
            )
            Spacer(Modifier.height(16.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row {
                        DetailField("Typical amount", amountText(series), Modifier.weight(1f))
                        if (pill == SubscriptionPill.ENDED) {
                            DetailField("Last payment", series.lastDate.format(FULL_DATE), Modifier.weight(1f))
                        } else {
                            DetailField("Next payment", series.nextExpected.format(FULL_DATE), Modifier.weight(1f))
                        }
                    }
                    Row {
                        Column(modifier = Modifier.weight(1f)) {
                            DetailLabel("Status")
                            Spacer(Modifier.height(4.dp))
                            StatusPill(pill)
                        }
                        DetailField(
                            "Since",
                            "${series.firstDate.format(MONTH_YEAR)} · ${series.occurrences} payments",
                            Modifier.weight(1f)
                        )
                    }
                    if (series.amountPattern == AmountPattern.VARIABLE) {
                        DetailField(
                            "Range",
                            "${formatMoney(series.minMinor, series.currency)} – " +
                                formatMoney(series.maxMinor, series.currency)
                        )
                    }
                    if (series.cycle != RecurringCycle.MONTHLY) {
                        DetailField(
                            "Per month",
                            formatMoney(Subscriptions.monthlyMinor(series), series.currency)
                        )
                    }
                }
            }
            series.priceChange?.takeIf { pill == SubscriptionPill.PRICE_CHANGED }?.let { change ->
                val direction = if (change.toMinor > change.fromMinor) "up" else "down"
                ReviewBanner(
                    title = "Price went $direction from ${formatMoney(change.fromMinor, series.currency)} " +
                        "to ${formatMoney(change.toMinor, series.currency)}",
                    body = "First charged on ${change.on.format(FULL_DATE)}.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            SectionRow(title = "Repeats", action = null)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RecurringCycle.entries.forEach { cycle ->
                    LedgerChip(
                        text = cycle.label,
                        selected = cycle == series.cycle,
                        onClick = { if (cycle != series.cycle) onSetCycle(cycle) }
                    )
                }
            }
            Text(
                "Wrong? Pick how often it really repeats. Next dates and alerts follow your choice.",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(Modifier.height(16.dp))
            SectionRow(title = "Past payments", action = null)
            Spacer(Modifier.height(6.dp))
            LedgerCard {
                Column {
                    history.forEachIndexed { index, payment ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                Instant.ofEpochMilli(payment.occurredAt).atZone(zone).toLocalDate().format(FULL_DATE),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ledger.Ink,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatMoney(payment.amountMinor, payment.currency),
                                style = MaterialTheme.typography.titleSmall,
                                color = Ledger.Ink
                            )
                        }
                        if (index != history.lastIndex) LedgerDivider()
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (renaming) {
                LedgerTextField(value = name, onValueChange = { name = it }, placeholder = "Subscription name")
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Save name", onClick = { onRename(name) }, enabled = name.isNotBlank())
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Cancel", onClick = { renaming = false })
            } else {
                if (series.confirmed) {
                    Text(
                        "You confirmed this subscription.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                } else {
                    PrimaryButton("Confirm subscription", onClick = onConfirm)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Rename", onClick = { renaming = true }, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onDismissSeries,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Ledger.Danger.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Ledger.DangerSoft,
                            contentColor = Ledger.Danger
                        )
                    ) {
                        Text("Dismiss", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLabel(text: String) {
    Text(text.uppercase(Locale.ENGLISH), style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
}

@Composable
private fun DetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        DetailLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
    }
}
