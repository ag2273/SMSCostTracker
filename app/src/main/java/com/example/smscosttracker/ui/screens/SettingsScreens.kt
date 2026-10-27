package com.example.smscosttracker.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.smscosttracker.data.DetectedInstrument
import com.example.smscosttracker.data.ImportProgress
import com.example.smscosttracker.data.ImportResult
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.data.UnparsedSms
import com.example.smscosttracker.ml.MessageType
import com.example.smscosttracker.parsing.TransactionParser
import com.example.smscosttracker.sms.SmsReader
import com.example.smscosttracker.sms.SmsSync
import com.example.smscosttracker.sms.SmsThread
import com.example.smscosttracker.ui.components.ChoiceRow
import com.example.smscosttracker.ui.components.EmptyState
import com.example.smscosttracker.ui.components.IconTile
import com.example.smscosttracker.ui.components.LedgerCard
import com.example.smscosttracker.ui.components.LedgerDivider
import com.example.smscosttracker.ui.components.LedgerTextButton
import com.example.smscosttracker.ui.components.PageHeader
import com.example.smscosttracker.ui.components.Pill
import com.example.smscosttracker.ui.components.PillTone
import com.example.smscosttracker.ui.components.PrimaryButton
import com.example.smscosttracker.ui.components.ReviewBanner
import com.example.smscosttracker.ui.components.ScreenBackground
import com.example.smscosttracker.ui.components.SecondaryButton
import com.example.smscosttracker.ui.components.SelectRow
import com.example.smscosttracker.ui.components.rememberCsvExport
import com.example.smscosttracker.ui.sheets.MessageLabelSheet
import com.example.smscosttracker.ui.theme.Ledger
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

enum class SettingsRoute {
    OVERVIEW,
    INSTRUMENTS,
    UNPARSED,
    REIMPORT,
    EXPORT
}

private data class SettingsStats(
    val transactions: Int = 0,
    val rawMessages: Int = 0,
    val pendingInstruments: Int = 0,
    val detectedInstruments: Int = 0,
    val unparsed: Int = 0,
    val loading: Boolean = true
)

@Composable
fun SettingsScreen(
    repository: TransactionRepository,
    preferences: OnboardingPreferences,
    refreshKey: Int,
    onOpenInstruments: () -> Unit,
    onOpenUnparsed: () -> Unit,
    onOpenReimport: () -> Unit,
    onOpenExport: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    var stats by remember { mutableStateOf(SettingsStats()) }
    var autoSync by remember { mutableStateOf(preferences.autoSync) }
    val lastSyncedAt = remember(refreshKey) { preferences.lastSyncedAt }
    val syncFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault())
    }

    LaunchedEffect(refreshKey) {
        thread {
            val all = repository.allDetectedInstruments()
            val loaded = SettingsStats(
                transactions = repository.count(),
                rawMessages = repository.rawMessageCount(),
                pendingInstruments = all.count { !it.verified },
                detectedInstruments = all.count { !it.ignored },
                unparsed = repository.unparsedMessages().size,
                loading = false
            )
            Handler(Looper.getMainLooper()).post { stats = loaded }
        }
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            PageHeader("Settings", "On this device")
            Spacer(Modifier.height(16.dp))

            if (!stats.loading && stats.pendingInstruments > 0) {
                ReviewBanner(
                    title = "${stats.pendingInstruments} card/account values need review",
                    body = "Confirm them to improve your totals.",
                    onClick = onOpenInstruments
                )
                Spacer(Modifier.height(16.dp))
            }

            SectionLabel("IMPORT")
            LedgerCard {
                Column {
                    SelectRow(
                        label = "Cards & accounts",
                        value = if (stats.loading) "Loading…" else
                            "${stats.detectedInstruments} values · ${stats.pendingInstruments} need review",
                        icon = Icons.Outlined.CreditCard,
                        onClick = onOpenInstruments
                    )
                    LedgerDivider()
                    SelectRow(
                        label = "Unparsed messages",
                        value = if (stats.loading) "Loading…" else "${stats.unparsed} kept for review",
                        icon = Icons.Outlined.WarningAmber,
                        onClick = onOpenUnparsed
                    )
                    LedgerDivider()
                    SelectRow(
                        label = "Sync new messages automatically",
                        value = if (lastSyncedAt > 0) {
                            "Last synced ${syncFormatter.format(Instant.ofEpochMilli(lastSyncedAt))}"
                        } else {
                            "Checks selected threads when you open the app and every 6 hours"
                        },
                        icon = Icons.Outlined.Sync,
                        trailing = if (autoSync) "On" else "Off",
                        onClick = {
                            autoSync = !autoSync
                            preferences.autoSync = autoSync
                            if (autoSync) SmsSync.schedule(context) else SmsSync.cancel(context)
                        }
                    )
                    LedgerDivider()
                    SelectRow(
                        label = "Re-import SMS",
                        value = "Scan selected threads again",
                        icon = Icons.Outlined.Sync,
                        onClick = onOpenReimport
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("EXPORT")
            LedgerCard {
                Column {
                    SelectRow(
                        label = "Export CSV",
                        value = "Download transactions as a spreadsheet",
                        icon = Icons.Outlined.FileDownload,
                        onClick = onOpenExport
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("STORAGE")
            LedgerCard {
                Column {
                    StatRow(
                        icon = Icons.Outlined.Storage,
                        label = "Transactions",
                        value = if (stats.loading) "…" else "%,d".format(stats.transactions)
                    )
                    LedgerDivider()
                    StatRow(
                        icon = Icons.Outlined.Refresh,
                        label = "Stored SMS",
                        value = if (stats.loading) "…" else "%,d".format(stats.rawMessages)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("PRIVACY")
            LedgerCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Ledger.Primary,
                        modifier = Modifier.size(19.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Everything stays on this phone",
                            style = MaterialTheme.typography.titleSmall,
                            color = Ledger.Ink
                        )
                        Text(
                            "SMS is read and parsed locally. The app has no internet permission and never uploads messages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ledger.Muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("ABOUT")
            LedgerCard {
                Column {
                    StatRow(
                        icon = Icons.Outlined.Info,
                        label = "SMS Cost Tracker",
                        value = "Version $versionName"
                    )
                    LedgerDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconTile(Icons.Outlined.Check)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Onboarding",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ledger.Ink
                            )
                            Text(
                                if (preferences.complete) "Completed" else "Not completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ledger.Muted
                            )
                        }
                        LedgerTextButton("Restart", onClick = onRestartOnboarding)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Ledger.Muted,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp)
    )
}

@Composable
private fun StatRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        IconTile(icon)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
        Text(value, style = MaterialTheme.typography.labelMedium, color = Ledger.Muted)
    }
}

private data class InstrumentChoice(
    val channel: TransactionChannel,
    val ignored: Boolean
)

@Composable
fun InstrumentsScreen(
    repository: TransactionRepository,
    importer: TransactionImporter,
    refreshKey: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    var instruments by remember { mutableStateOf<List<DetectedInstrument>>(emptyList()) }
    var initial by remember { mutableStateOf<Map<String, InstrumentChoice>>(emptyMap()) }
    var choices by remember { mutableStateOf<Map<String, InstrumentChoice>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun key(instrument: DetectedInstrument) =
        TransactionParser.instrumentKey(instrument.address, instrument.digits)

    fun load() {
        loading = true
        thread {
            val loaded = repository.allDetectedInstruments()
            val mapped = loaded.associate {
                key(it) to InstrumentChoice(
                    channel = it.channel,
                    ignored = it.ignored
                )
            }
            Handler(Looper.getMainLooper()).post {
                instruments = loaded
                initial = mapped
                choices = mapped
                loading = false
            }
        }
    }

    LaunchedEffect(refreshKey) { load() }

    val groups = instruments.groupBy { it.address }.toList()
        .sortedBy { it.first }
    val pending = instruments.count { !it.verified && !it.ignored }
    val dirty = choices != initial

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            PageHeader(
                title = "Cards & accounts",
                subtitle = if (pending > 0) "$pending values need review" else "All values confirmed",
                onBack = onBack
            )
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (instruments.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.CreditCard,
                    title = "No card or account values yet",
                    body = "Import bank SMS first and every detected number will appear here."
                )
            } else {
                if (pending > 0) {
                    ReviewBanner(
                        title = "$pending values need review",
                        body = "Tagging a card or account improves future imports.",
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                Text(
                    "Tap Card, Account or Ignore. Saving rebuilds transactions from stored SMS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    groups.forEach { (address, rows) ->
                        item(key = "group-$address") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.AccountBalance,
                                    contentDescription = null,
                                    tint = Ledger.Muted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    address,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Ledger.Muted,
                                    modifier = Modifier.padding(start = 5.dp)
                                )
                            }
                        }
                        items(rows, key = { key(it) }) { instrument ->
                            InstrumentSettingCard(
                                instrument = instrument,
                                choice = choices[key(instrument)] ?: InstrumentChoice(instrument.channel, instrument.ignored),
                                onChoice = { choice ->
                                    choices = choices + (key(instrument) to choice)
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = Ledger.Danger)
                }
                if (saving) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Ledger.Primary
                        )
                        Text(
                            "Applying and rebuilding transactions…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Ledger.Muted
                        )
                    }
                } else {
                    PrimaryButton(
                        text = "Save changes",
                        enabled = dirty,
                        onClick = {
                            saving = true
                            error = null
                            val toSave = instruments.map { instrument ->
                                val choice = choices[key(instrument)]
                                    ?: InstrumentChoice(instrument.channel, instrument.ignored)
                                instrument.copy(
                                    channel = choice.channel,
                                    ignored = choice.ignored
                                )
                            }
                            thread {
                                try {
                                    repository.saveInstruments(toSave)
                                    importer.rebuildFromStored()
                                    Handler(Looper.getMainLooper()).post {
                                        saving = false
                                        onChanged()
                                    }
                                } catch (t: Throwable) {
                                    Handler(Looper.getMainLooper()).post {
                                        saving = false
                                        error = t.message ?: "Could not save instrument changes."
                                    }
                                }
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun InstrumentSettingCard(
    instrument: DetectedInstrument,
    choice: InstrumentChoice,
    onChoice: (InstrumentChoice) -> Unit
) {
    LedgerCard {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(instrument.digits, style = MaterialTheme.typography.titleMedium, color = Ledger.Ink)
                    Text(
                        "${instrument.messageCount} message(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ledger.Muted
                    )
                }
                if (choice.ignored) {
                    Pill("Ignored", PillTone.Neutral)
                } else if (instrument.verified) {
                    Pill("Confirmed", PillTone.Success)
                } else {
                    Pill("Review", PillTone.Accent)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ChoicePill(
                    label = "Card",
                    selected = !choice.ignored && choice.channel == TransactionChannel.CARD,
                    onClick = { onChoice(choice.copy(channel = TransactionChannel.CARD, ignored = false)) },
                    modifier = Modifier.weight(1f)
                )
                ChoicePill(
                    label = "Account",
                    selected = !choice.ignored && choice.channel == TransactionChannel.ACCOUNT,
                    onClick = { onChoice(choice.copy(channel = TransactionChannel.ACCOUNT, ignored = false)) },
                    modifier = Modifier.weight(1f)
                )
                ChoicePill(
                    label = "Ignore",
                    selected = choice.ignored,
                    onClick = { onChoice(choice.copy(ignored = true)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) Ledger.PrimarySoft else Ledger.Surface,
        border = BorderStroke(1.dp, if (selected) Ledger.Primary else Ledger.Outline)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Ledger.Primary else Ledger.Ink
            )
        }
    }
}

@Composable
fun ExportScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    onBack: () -> Unit
) {
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    val export = rememberCsvExport { status = it }

    LaunchedEffect(refreshKey) {
        loading = true
        thread {
            val loaded = repository.transactions()
            Handler(Looper.getMainLooper()).post {
                transactions = loaded
                loading = false
            }
        }
    }

    val currencies = transactions.map { it.currency }.filter { it.isNotBlank() }.distinct().sorted()
    val scope = when {
        loading -> "Loading…"
        transactions.isEmpty() -> "No transactions to export"
        currencies.isEmpty() -> "CSV · ${"%,d".format(transactions.size)} transactions"
        else -> "CSV · ${"%,d".format(transactions.size)} transactions · ${currencies.joinToString(" + ")}"
    }

    ScreenBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            PageHeader(
                title = "Export & backup",
                subtitle = "You choose what leaves this phone",
                onBack = onBack
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel("EXPORT")
            LedgerCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Icon(
                            Icons.Outlined.FileDownload,
                            contentDescription = null,
                            tint = Ledger.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Export transactions",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ledger.Ink
                            )
                            Text(scope, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
                        }
                    }
                    Text(
                        "Dates, merchants, categories, amounts, currencies and instruments. No SMS text is included.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Export CSV",
                        onClick = { export(transactions) },
                        enabled = !loading && transactions.isNotEmpty()
                    )
                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Ledger.Primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("PRIVACY")
            LedgerCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "What leaves your phone?",
                        style = MaterialTheme.typography.titleSmall,
                        color = Ledger.Ink
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Only the CSV file you explicitly create. SMS text stays on this device and is never uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Muted
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}

@Composable
fun UnparsedMessagesScreen(
    repository: TransactionRepository,
    refreshKey: Int,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    var messages by remember { mutableStateOf<List<UnparsedSms>>(emptyList()) }
    var labelling by remember { mutableStateOf<UnparsedSms?>(null) }
    var loading by remember { mutableStateOf(true) }
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault())
    }

    LaunchedEffect(refreshKey) {
        loading = true
        thread {
            val loaded = repository.unparsedMessages()
            Handler(Looper.getMainLooper()).post {
                messages = loaded
                loading = false
            }
        }
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            PageHeader(
                title = "Unparsed messages",
                subtitle = if (messages.isEmpty()) "Nothing waiting" else "${messages.size} messages kept",
                onBack = onBack
            )
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else if (messages.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Check,
                    title = "Everything parsed",
                    body = "Messages that cannot be parsed will stay here for review."
                )
            } else {
                Text(
                    "These messages had spending clues but no reliable amount, date or instrument. They are never turned into transactions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(messages) { message ->
                        LedgerCard(modifier = Modifier.padding(bottom = 8.dp)) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        message.address,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Ledger.Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Pill(message.reason, PillTone.Accent)
                                }
                                Text(
                                    formatter.format(Instant.ofEpochMilli(message.date)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ledger.Muted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    message.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Ledger.Ink,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                LedgerTextButton(
                                    text = "Teach how to read this",
                                    onClick = { labelling = message }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }

    labelling?.let { message ->
        MessageLabelSheet(
            rawSmsId = message.rawSmsId,
            address = message.address,
            body = message.body,
            repository = repository,
            initialType = MessageType.PURCHASE,
            onDismiss = { labelling = null },
            onSaved = {
                labelling = null
                onChanged()
            }
        )
    }
}

@Composable
fun ReimportScreen(
    importer: TransactionImporter,
    preferences: OnboardingPreferences,
    onBack: () -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasSmsPermission(context)) }
    var threads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var selected by remember { mutableStateOf(preferences.selectedThreadIds) }
    var loading by remember { mutableStateOf(granted) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<ImportProgress?>(null) }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasSmsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        granted = isGranted
        if (!isGranted) error = "SMS access is off. Open app settings to allow it."
    }

    LaunchedEffect(granted) {
        if (!granted) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        thread {
            runCatching { SmsReader.readThreads(context) }
                .onSuccess { loaded ->
                    Handler(Looper.getMainLooper()).post {
                        threads = loaded
                        loading = false
                    }
                }
                .onFailure {
                    Handler(Looper.getMainLooper()).post {
                        error = it.message ?: "Could not read SMS threads."
                        loading = false
                    }
                }
        }
    }

    fun scan() {
        if (selected.isEmpty()) {
            error = "Choose at least one thread."
            return
        }
        preferences.selectedThreadIds = selected
        scanning = true
        result = null
        error = null
        progress = null
        thread {
            try {
                val messages = selected.flatMap { SmsReader.readMessages(context, it) }
                val imported = importer.import(messages) { p ->
                    Handler(Looper.getMainLooper()).post { progress = p }
                }
                Handler(Looper.getMainLooper()).post {
                    scanning = false
                    result = imported
                }
            } catch (t: Throwable) {
                Handler(Looper.getMainLooper()).post {
                    scanning = false
                    error = t.message ?: "Import failed."
                }
            }
        }
    }

    ScreenBackground {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            PageHeader(
                title = "Re-import SMS",
                subtitle = "Only selected threads are scanned",
                onBack = onBack
            )
            if (result != null) {
                val imported = result!!
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Surface(
                        modifier = Modifier.size(72.dp).align(Alignment.CenterHorizontally),
                        shape = CircleShape,
                        color = Ledger.SurfaceSoft
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Ledger.Success,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                    Text(
                        "Import complete",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ledger.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Text(
                        "${imported.stored} new transactions · ${imported.skipped} skipped · ${imported.unparsed} unparsed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ledger.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(22.dp))
                    PrimaryButton("Done", onFinished)
                }
            } else if (!granted) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "SMS access is off. Allow access to re-scan your bank threads.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ledger.Muted
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Allow SMS access", { permissionLauncher.launch(Manifest.permission.READ_SMS) })
                SecondaryButton(
                    text = "Open app settings",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else if (scanning) {
                Spacer(Modifier.height(22.dp))
                Text("Reading selected threads", style = MaterialTheme.typography.headlineMedium, color = Ledger.Ink)
                Text(
                    "Balance lines, OTPs and duplicates are skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Ledger.Muted
                )
                Spacer(Modifier.height(16.dp))
                LedgerCard {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Progress",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                color = Ledger.Ink
                            )
                            Text(
                                "${progress?.processed ?: 0} / ${progress?.total ?: 0}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Ledger.Primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = if ((progress?.total ?: 0) == 0) 0f else {
                                ((progress?.processed ?: 0).toFloat() / progress!!.total).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                            color = Ledger.Primary,
                            trackColor = Ledger.SurfaceAlt
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReimportStat("Parsed", progress?.parsed ?: 0, Modifier.weight(1f))
                            ReimportStat("Skipped", progress?.skipped ?: 0, Modifier.weight(1f))
                            ReimportStat("Unparsed", progress?.unparsed ?: 0, Modifier.weight(1f))
                        }
                    }
                }
            } else if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ledger.Primary)
                }
            } else {
                Text(
                    "${selected.size} selected · tap to change",
                    style = MaterialTheme.typography.labelMedium,
                    color = Ledger.Muted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(threads, key = { it.threadId }) { thread ->
                        ReimportThreadRow(
                            thread = thread,
                            checked = thread.threadId in selected,
                            onToggle = {
                                selected = if (thread.threadId in selected) {
                                    selected - thread.threadId
                                } else {
                                    selected + thread.threadId
                                }
                            }
                        )
                        LedgerDivider()
                    }
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Ledger.Danger,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                PrimaryButton(
                    text = if (selected.isEmpty()) "Choose a thread" else "Re-scan ${selected.size} threads",
                    onClick = ::scan,
                    enabled = selected.isNotEmpty()
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReimportThreadRow(
    thread: SmsThread,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(thread.address, style = MaterialTheme.typography.titleSmall, color = Ledger.Ink)
            Text(
                thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = Ledger.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${thread.messageCount} messages",
                style = MaterialTheme.typography.labelSmall,
                color = Ledger.Muted
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ReimportStat(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Ledger.SurfaceAlt
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Ledger.Muted)
            Text(
                "%,d".format(value),
                style = MaterialTheme.typography.titleMedium,
                color = Ledger.Ink
            )
        }
    }
}

private fun hasSmsPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED
