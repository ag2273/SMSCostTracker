package com.example.smscosttracker.ui.sheets

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.analysis.TransactionReview
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.InstrumentCandidate
import com.example.smscosttracker.data.MessageLabelEntity
import com.example.smscosttracker.data.RawSmsEntity
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.ui.components.Avatar
import com.example.smscosttracker.ui.components.ChannelIcon
import com.example.smscosttracker.ui.components.ChoiceRow
import com.example.smscosttracker.ui.components.DropdownChip
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerChip
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerTextButton
import com.example.smscosttracker.ui.components.LedgerTextField
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.SecondaryButton
import com.example.smscosttracker.ui.components.SheetHeader
import com.example.smscosttracker.ui.components.channelLabel
import com.example.smscosttracker.ui.components.formatMoney
import com.example.smscosttracker.ui.theme.Ledger
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

private val CUSTOM_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(
    filter: TransactionFilterState,
    accounts: List<String>,
    currencies: List<String>,
    allTransactions: List<TransactionEntity>,
    onDismiss: () -> Unit,
    onApply: (TransactionFilterState) -> Unit
) {
    var draft by remember(filter) { mutableStateOf(filter) }
    val previewCount = remember(draft, allTransactions) {
        TransactionFilters.apply(allTransactions, draft).size
    }
    val customStart = draft.customStart
    val customEnd = draft.customEnd
    val customRangeError = when {
        draft.preset != DatePreset.CUSTOM -> null
        customStart == null -> "Choose a start date for the custom range."
        customEnd != null && customEnd.isBefore(customStart) ->
            "End date must be on or after the start date."
        else -> null
    }
    val customRangeValid = customRangeError == null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                title = "Filters",
                subtitle = "Narrow transactions without leaving Activity",
                onClose = onDismiss
            )
            Spacer(Modifier.height(18.dp))
            Text("When", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                DatePreset.entries.forEach { preset ->
                    LedgerChip(
                        text = preset.label,
                        selected = draft.preset == preset,
                        onClick = {
                            draft = if (preset == DatePreset.CUSTOM && draft.customStart == null) {
                                val today = LocalDate.now()
                                draft.copy(
                                    preset = preset,
                                    customStart = today.withDayOfMonth(1),
                                    customEnd = today
                                )
                            } else {
                                draft.copy(preset = preset)
                            }
                        }
                    )
                }
            }
            if (draft.preset == DatePreset.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                CustomRangeFields(
                    start = draft.customStart,
                    end = draft.customEnd,
                    onStartChange = { draft = draft.copy(customStart = it) },
                    onEndChange = { draft = draft.copy(customEnd = it) }
                )
                customRangeError?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.Danger
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Category",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ledger.Ink
                )
                LedgerTextButton(
                    text = "All categories",
                    onClick = { draft = draft.copy(category = null) }
                )
            }
            Spacer(Modifier.height(4.dp))
            CategoryGrid(
                selected = draft.category,
                query = "",
                onSelect = { draft = draft.copy(category = it) }
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownChip(
                    label = draft.account ?: "All accounts",
                    options = listOf("All accounts") + accounts,
                    onSelect = {
                        draft = draft.copy(account = it.takeUnless { value -> value == "All accounts" })
                    }
                )
                DropdownChip(
                    label = draft.currency ?: "All currencies",
                    options = listOf("All currencies") + currencies,
                    onSelect = {
                        draft = draft.copy(currency = it.takeUnless { value -> value == "All currencies" })
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
            LedgerTextField(
                value = draft.merchantQuery,
                onValueChange = { draft = draft.copy(merchantQuery = it) },
                placeholder = "Merchant contains…",
                leadingIcon = Icons.Outlined.Search
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (customRangeValid) {
                        "$previewCount matching transactions"
                    } else {
                        "Choose a valid custom range"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (customRangeValid) Ledger.Muted else Ledger.Danger,
                    modifier = Modifier.weight(1f)
                )
                LedgerTextButton(
                    text = "Clear all",
                    onClick = {
                        draft = TransactionFilterState(
                            preset = DatePreset.THIS_MONTH,
                            currency = currencies.firstOrNull()
                        )
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = if (customRangeValid) {
                    "Show $previewCount transactions"
                } else {
                    "Choose a valid date range"
                },
                onClick = { onApply(draft) },
                enabled = customRangeValid
            )
        }
    }
}

@Composable
private fun CustomRangeFields(
    start: LocalDate?,
    end: LocalDate?,
    onStartChange: (LocalDate?) -> Unit,
    onEndChange: (LocalDate?) -> Unit
) {
    var picking by remember { mutableStateOf<DateTarget?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateField(
            label = "From",
            date = start,
            modifier = Modifier.weight(1f),
            onClick = { picking = DateTarget.START }
        )
        DateField(
            label = "To",
            date = end,
            modifier = Modifier.weight(1f),
            onClick = { picking = DateTarget.END }
        )
    }
    if (start != null || end != null) {
        LedgerTextButton(
            text = "Clear custom dates",
            onClick = {
                onStartChange(null)
                onEndChange(null)
            }
        )
    }

    val target = picking
    if (target != null) {
        val isStart = target == DateTarget.START
        DatePickerModal(
            initial = if (isStart) start else end ?: start,
            onDismiss = { picking = null },
            onSelect = {
                if (isStart) onStartChange(it) else onEndChange(it)
                picking = null
            }
        )
    }
}

private enum class DateTarget { START, END }

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Ledger.Surface,
        border = BorderStroke(1.dp, Ledger.Outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Ledger.Muted,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                Text(
                    date?.format(CUSTOM_DATE_FORMAT) ?: "Select date",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (date == null) Ledger.Muted else Ledger.Ink
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onSelect: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.toPickerMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onSelect(it.toPickerDate()) }
                        ?: onDismiss()
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.toPickerMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toPickerDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailSheet(
    transaction: TransactionEntity,
    repository: TransactionRepository,
    onDismiss: () -> Unit,
    onCategory: () -> Unit,
    onChanged: () -> Unit
) {
    var messages by remember { mutableStateOf<List<RawSmsEntity>>(emptyList()) }
    var candidates by remember { mutableStateOf<List<InstrumentCandidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showInstruments by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf<MessageLabelEntity?>(null) }
    var labelling by remember { mutableStateOf<RawSmsEntity?>(null) }
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
    }

    LaunchedEffect(transaction.id) {
        thread {
            val loadedMessages = repository.sourceMessagesFor(transaction)
            val loadedCandidates = repository.candidateInstruments(transaction)
            val loadedLabel = loadedMessages.firstOrNull()?.let { repository.messageLabel(it.id) }
            Handler(Looper.getMainLooper()).post {
                messages = loadedMessages
                candidates = loadedCandidates
                label = loadedLabel
                loading = false
            }
        }
    }

    labelling?.let { message ->
        MessageLabelSheet(
            rawSmsId = message.id,
            address = message.address,
            body = message.body,
            repository = repository,
            initialType = repository.ruleType(message),
            initialMerchant = transaction.merchant.takeIf { it != "Unknown" }.orEmpty(),
            initialAmount = BigDecimal.valueOf(transaction.amountMinor, 2).toPlainString(),
            initialInstrument = transaction.account.substringAfterLast(' ')
                .takeIf { digits -> digits.isNotEmpty() && digits.all { it.isDigit() } }
                .orEmpty(),
            onDismiss = { labelling = null },
            onSaved = {
                labelling = null
                onChanged()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                title = "Transaction",
                subtitle = "Parsed from ${messages.size.coerceAtLeast(1)} SMS",
                onClose = onDismiss
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Avatar(
                    letter = transaction.merchant,
                    color = Ledger.Primary,
                    size = 42
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        transaction.merchant,
                        style = MaterialTheme.typography.titleLarge,
                        color = Ledger.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatter.format(Instant.ofEpochMilli(transaction.occurredAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.Muted
                    )
                }
                Text(
                    "-" + formatMoney(transaction.amountMinor, transaction.currency),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ledger.Ink
                )
            }
            Spacer(Modifier.height(14.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(15.dp)) {
                    Text("What we parsed", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParsedField("AMOUNT", formatMoney(transaction.amountMinor, transaction.currency), Modifier.weight(1f))
                        ParsedField("MERCHANT", transaction.merchant, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParsedField(
                            "CATEGORY",
                            listOfNotNull(
                                Category.fromStorage(transaction.category).displayName,
                                TransactionReview.confidenceLabel(transaction)
                            ).joinToString(" · "),
                            Modifier.weight(1f)
                        )
                        ParsedField(
                            "INSTRUMENT",
                            if (transaction.account.isBlank()) "Unknown" else transaction.account,
                            Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ParsedField(
                            "OCCURRED",
                            formatter.format(Instant.ofEpochMilli(transaction.occurredAt)),
                            Modifier.weight(1f)
                        )
                        ParsedField("SOURCE", transaction.sourceAddress, Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Source SMS",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = Ledger.Ink
                        )
                        Pill("${messages.size} message(s)", PillTone.Neutral)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
                            strokeWidth = 2.dp,
                            color = Ledger.Primary
                        )
                    } else if (messages.isEmpty()) {
                        Text(
                            "No matching source SMS found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ledger.Muted
                        )
                    } else {
                        messages.forEach { message ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Ledger.SurfaceAlt,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)
                            ) {
                                Column(modifier = Modifier.padding(11.dp)) {
                                    Text(
                                        "${message.address} · ${formatter.format(Instant.ofEpochMilli(message.date))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Ledger.Muted
                                    )
                                    Text(
                                        message.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Ledger.Ink,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LedgerCard {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Why this result", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    WhyRow(
                        if (transaction.merchant == "Unknown") {
                            "No merchant name was recognised in the message text."
                        } else {
                            "Merchant was read from the message text."
                        }
                    )
                    WhyRow(
                        if (transaction.account.isBlank()) {
                            "No instrument matched; this transaction is tagged for review."
                        } else {
                            "Instrument ${transaction.account} was read from the SMS."
                        }
                    )
                    if (label != null) {
                        WhyRow("Read with your correction of this sender's message format.")
                    }
                    WhyRow("Balance lines and duplicates are skipped.")
                }
            }
            if (showInstruments && candidates.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LedgerCard {
                    Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Change card/account", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                        candidates.forEach { candidate ->
                            ChoiceRow(
                                label = "${channelLabel(candidate.channel)} · ${candidate.digits}",
                                selected = transaction.account.endsWith(candidate.digits),
                                onClick = {
                                    thread {
                                        repository.assignInstrument(transaction, candidate)
                                        Handler(Looper.getMainLooper()).post { onChanged() }
                                    }
                                }
                            )
                        }
                        Text(
                            "Saved for this transaction and this sender. Future imports use it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ledger.Muted
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Change category", onCategory)
            SecondaryButton(
                text = "Change card/account",
                onClick = { showInstruments = !showInstruments },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (messages.isNotEmpty()) {
                SecondaryButton(
                    text = "Fix how this SMS is read",
                    onClick = { labelling = messages.first() },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageLabelSheet(
    rawSmsId: Long,
    address: String,
    body: String,
    repository: TransactionRepository,
    initialType: MessageType,
    initialMerchant: String = "",
    initialAmount: String = "",
    initialInstrument: String = "",
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var type by remember(rawSmsId) { mutableStateOf(initialType) }
    var merchant by remember(rawSmsId) { mutableStateOf(initialMerchant) }
    var amount by remember(rawSmsId) { mutableStateOf(initialAmount) }
    var instrument by remember(rawSmsId) { mutableStateOf(initialInstrument) }
    var existing by remember(rawSmsId) { mutableStateOf<MessageLabelEntity?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(rawSmsId) {
        thread {
            val stored = repository.messageLabel(rawSmsId)
            Handler(Looper.getMainLooper()).post {
                existing = stored
                stored?.let {
                    type = MessageType.fromStorage(it.type)
                    merchant = it.merchant.orEmpty()
                    amount = it.amount.orEmpty()
                    instrument = it.instrument.orEmpty()
                }
            }
        }
    }

    fun persist(action: () -> Unit) {
        saving = true
        thread {
            action()
            Handler(Looper.getMainLooper()).post {
                saving = false
                onSaved()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                title = "Fix how this SMS is read",
                subtitle = address,
                onClose = onDismiss
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Ledger.SurfaceAlt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Ink,
                    modifier = Modifier.padding(11.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("This message is", style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                MessageType.entries.forEach { option ->
                    ChoiceRow(
                        label = option.label,
                        selected = type == option,
                        onClick = { type = option }
                    )
                }
            }
            if (type.isSpending) {
                Spacer(Modifier.height(14.dp))
                Text("MERCHANT", style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                LedgerTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    placeholder = "Copy the shop name as written in the SMS"
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AMOUNT", style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                        LedgerTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            placeholder = "1,249.00"
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CARD/ACCOUNT", style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                        LedgerTextField(
                            value = instrument,
                            onValueChange = { value -> instrument = value.filter { it.isDigit() } },
                            placeholder = "Last digits"
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Ledger.AccentSoft,
                border = BorderStroke(1.dp, Ledger.Accent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Text("This teaches the reader", style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                    Text(
                        "Every SMS from $address with the same wording will be read the same way, " +
                            "now and in future imports. It stays on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                    strokeWidth = 2.dp,
                    color = Ledger.Primary
                )
            } else {
                PrimaryButton(
                    "Save and re-read messages",
                    {
                        persist {
                            repository.saveMessageLabel(
                                rawSmsId = rawSmsId,
                                type = type,
                                merchant = merchant.takeIf { type.isSpending },
                                amount = amount.takeIf { type.isSpending },
                                instrument = instrument.takeIf { type.isSpending }
                            )
                        }
                    }
                )
                if (existing != null) {
                    LedgerTextButton(
                        text = "Remove my correction",
                        onClick = { persist { repository.clearMessageLabel(rawSmsId) } },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                LedgerTextButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun ParsedField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = Ledger.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WhyRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = Ledger.Success,
            modifier = Modifier.size(15.dp)
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ledger.Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    transaction: TransactionEntity,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    val current = Category.fromStorage(transaction.category)
    var selected by remember(transaction.id) { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                title = "Change category",
                subtitle = "${transaction.merchant} · ${formatMoney(transaction.amountMinor, transaction.currency)}",
                onClose = onDismiss
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Pill("Current: ${current.displayName}", PillTone.Accent)
                Text("→", style = MaterialTheme.typography.titleSmall, color = Ledger.Muted)
                Pill("New: ${selected.displayName}", PillTone.Primary)
            }
            Spacer(Modifier.height(12.dp))
            LedgerTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search ${Category.entries.size} categories",
                leadingIcon = Icons.Outlined.Search
            )
            Spacer(Modifier.height(10.dp))
            CategoryGrid(
                selected = selected,
                query = query,
                onSelect = { selected = it }
            )
            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Ledger.AccentSoft,
                border = BorderStroke(1.dp, Ledger.Accent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Text("This teaches the classifier", style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
                    Text(
                        "All past transactions from ${transaction.merchant} will use ${selected.displayName}. " +
                            "Future imports will learn this too.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save correction", { onSave(selected) })
            LedgerTextButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    selected: Category?,
    query: String,
    onSelect: (Category) -> Unit
) {
    val categories = Category.entries.filter {
        query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true)
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = selected == category,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Ledger.Primary else Ledger.Surface,
        border = BorderStroke(1.dp, if (selected) Ledger.Primary else Ledger.Outline)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                category.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Ledger.OnPrimary else Ledger.Ink,
                maxLines = 1
            )
        }
    }
}
