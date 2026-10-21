package com.example.smscosttracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smscosttracker.analysis.AccountTotal
import com.example.smscosttracker.analysis.CategoryTotal
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.MerchantTotal
import com.example.smscosttracker.analysis.MonthlyTotal
import com.example.smscosttracker.analysis.SpendingAnalysis
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.RawSmsEntity
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.sms.SmsReader
import com.example.smscosttracker.sms.SmsThread
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

private enum class AppTab(val label: String) {
    IMPORT("Import"),
    TRANSACTIONS("Transactions"),
    CHARTS("Charts")
}

@Composable
private fun App() {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val importer = remember { TransactionImporter(database) }
    val repository = remember { TransactionRepository(database) }
    var tab by remember { mutableStateOf(AppTab.IMPORT) }
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab.ordinal) {
            AppTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(entry.label) }
                )
            }
        }
        when (tab) {
            AppTab.IMPORT -> ImportScreen(
                importer = importer,
                repository = repository,
                onImported = { refreshKey++ }
            )
            AppTab.TRANSACTIONS -> TransactionsScreen(
                repository = repository,
                refreshKey = refreshKey
            )
            AppTab.CHARTS -> ChartsScreen(repository = repository, refreshKey = refreshKey)
        }
    }
}

@Composable
private fun ImportScreen(
    importer: TransactionImporter,
    repository: TransactionRepository,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var threads by remember { mutableStateOf<List<SmsThread>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) status = "SMS permission is required to read threads."
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        loading = true
        thread {
            val loaded = SmsReader.readThreads(context)
            Handler(Looper.getMainLooper()).post {
                threads = loaded
                loading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select banking SMS threads", style = MaterialTheme.typography.titleLarge)
        Text("Imported messages stay on this device.", style = MaterialTheme.typography.bodySmall)

        if (!hasPermission) {
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.READ_SMS) },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Grant SMS access")
            }
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Open app settings")
            }
        } else if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(threads, key = { it.threadId }) { thread ->
                    ThreadRow(
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
                    Divider()
                }
            }
            Button(
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    val toImport = selected.toList()
                    loading = true
                    thread {
                        val messages = toImport.flatMap { SmsReader.readMessages(context, it) }
                        val result = importer.import(messages)
                        val total = repository.count()
                        Handler(Looper.getMainLooper()).post {
                            loading = false
                            onImported()
                            status = "Imported ${result.rawInserted} SMS, " +
                                "parsed ${result.parsed}, stored ${result.stored}. " +
                                "Transactions total: $total."
                        }
                    }
                }
            ) {
                Text("Import ${selected.size} thread(s)")
            }
            Button(
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onClick = {
                    loading = true
                    thread {
                        val result = importer.rebuildFromStored()
                        val total = repository.count()
                        Handler(Looper.getMainLooper()).post {
                            loading = false
                            onImported()
                            status = "Rebuilt from ${result.rawInserted} stored SMS, " +
                                "parsed ${result.parsed}, stored ${result.stored}. " +
                                "Transactions total: $total."
                        }
                    }
                }
            ) {
                Text("Rebuild from stored SMS")
            }
        }
        if (status.isNotEmpty()) {
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ThreadRow(thread: SmsThread, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(thread.address, style = MaterialTheme.typography.titleSmall)
            Text(
                thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text("${thread.messageCount} messages", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private class TransactionListState(
    val transactions: List<TransactionEntity>,
    val loading: Boolean,
    val reload: () -> Unit
)

@Composable
private fun rememberTransactions(
    repository: TransactionRepository,
    refreshKey: Int
): TransactionListState {
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey, version) {
        loading = true
        thread {
            val loaded = repository.transactions()
            Handler(Looper.getMainLooper()).post {
                transactions = loaded
                loading = false
            }
        }
    }

    return TransactionListState(transactions, loading) { version++ }
}

@Composable
private fun TransactionsScreen(repository: TransactionRepository, refreshKey: Int) {
    var filter by remember { mutableStateOf(TransactionFilterState()) }
    var detail by remember { mutableStateOf<TransactionEntity?>(null) }
    val state = rememberTransactions(repository, refreshKey)
    val filtered = TransactionFilters.apply(state.transactions, filter)
    val accounts = remember(state.transactions) {
        state.transactions.map { it.account }.filter { it.isNotBlank() }.distinct().sorted()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        FilterBar(filter = filter, accounts = accounts, onChange = { filter = it })
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
        } else if (filtered.isEmpty()) {
            Text("No transactions match these filters.", modifier = Modifier.padding(top = 16.dp))
        } else {
            Text(
                "${filtered.size} transaction(s)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        onDetails = { detail = transaction },
                        onCategorySelected = { category ->
                            thread {
                                repository.updateCategory(transaction, category)
                                Handler(Looper.getMainLooper()).post { state.reload() }
                            }
                        }
                    )
                    Divider()
                }
            }
        }
    }

    detail?.let { transaction ->
        TransactionDetailDialog(
            transaction = transaction,
            repository = repository,
            onDismiss = { detail = null }
        )
    }
}

@Composable
private fun ChartsScreen(repository: TransactionRepository, refreshKey: Int) {
    var filter by remember { mutableStateOf(TransactionFilterState()) }
    val state = rememberTransactions(repository, refreshKey)
    val filtered = TransactionFilters.apply(state.transactions, filter)
    val summary = remember(state.transactions, filter) { SpendingAnalysis.summarize(filtered) }
    val currency = summary.currency.ifBlank { "EGP" }
    val accounts = remember(state.transactions) {
        state.transactions.map { it.account }.filter { it.isNotBlank() }.distinct().sorted()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        FilterBar(filter = filter, accounts = accounts, onChange = { filter = it })
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
        } else if (filtered.isEmpty()) {
            Text("No transactions match these filters.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        "Total: ${formatAmount(summary.totalMinor, currency)}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                item {
                    SectionTitle("Spending by category")
                    CategoryPieChart(summary.byCategory, currency)
                }
                item {
                    SectionTitle("Spending by account")
                    AccountList(summary.byAccount, currency)
                }
                item {
                    SectionTitle("Monthly trend")
                    MonthlyBarChart(summary.byMonth, currency)
                }
                item {
                    SectionTitle("Top merchants")
                    TopMerchants(summary.topMerchants, currency)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun CategoryPieChart(totals: List<CategoryTotal>, currency: String) {
    Column {
        Canvas(modifier = Modifier.size(180.dp)) {
            val total = totals.sumOf { it.amountMinor }.coerceAtLeast(1)
            var start = -90f
            totals.forEachIndexed { index, totalForCategory ->
                val sweep = 360f * totalForCategory.amountMinor / total
                drawArc(
                    color = chartColors[index % chartColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true
                )
                start += sweep
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        totals.forEachIndexed { index, totalForCategory ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(chartColors[index % chartColors.size])
                )
                Text(
                    totalForCategory.category.displayName,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                Text(formatAmount(totalForCategory.amountMinor, currency))
            }
        }
    }
}

@Composable
private fun MonthlyBarChart(totals: List<MonthlyTotal>, currency: String) {
    val max = totals.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1) ?: 1
    val formatter = remember { DateTimeFormatter.ofPattern("MMM") }
    val yearFormatter = remember { DateTimeFormatter.ofPattern("MMM yy") }
    Row(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        totals.forEach { total ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    formatAmount(total.amountMinor, currency),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height((110f * total.amountMinor / max).dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    if (total.month.monthValue == 1) {
                        total.month.format(yearFormatter)
                    } else {
                        total.month.format(formatter)
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TopMerchants(merchants: List<MerchantTotal>, currency: String) {
    Column {
        merchants.forEach { merchant ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(merchant.merchant, modifier = Modifier.weight(1f))
                Text(formatAmount(merchant.amountMinor, currency))
            }
        }
    }
}

@Composable
private fun AccountList(accounts: List<AccountTotal>, currency: String) {
    Column {
        accounts.forEach { account ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(account.account, modifier = Modifier.weight(1f))
                Text(formatAmount(account.amountMinor, currency))
            }
        }
    }
}

private fun formatAmount(amountMinor: Long, currency: String): String =
    String.format(Locale.US, "%.2f %s", amountMinor / 100.0, currency)

private val chartColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFF4511E),
    Color(0xFF8E24AA),
    Color(0xFFFDD835),
    Color(0xFF00ACC1),
    Color(0xFFD81B60),
    Color(0xFF6D4C41),
    Color(0xFF3949AB),
    Color(0xFF7CB342),
    Color(0xFFFFB300),
    Color(0xFF00897B),
    Color(0xFF5E35B1),
    Color(0xFF546E7A),
    Color(0xFFC62828)
)

@Composable
private fun FilterBar(
    filter: TransactionFilterState,
    accounts: List<String>,
    onChange: (TransactionFilterState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(
                label = filter.preset.label,
                options = DatePreset.entries.map { it.label },
                onSelect = { label ->
                    val preset = DatePreset.entries.first { it.label == label }
                    onChange(filter.copy(preset = preset))
                }
            )
            Dropdown(
                label = filter.category?.displayName ?: "All categories",
                options = listOf("All categories") + Category.entries.map { it.displayName },
                onSelect = { label ->
                    val category = Category.entries.firstOrNull { it.displayName == label }
                    onChange(filter.copy(category = category))
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(
                label = filter.account ?: "All accounts",
                options = listOf("All accounts") + accounts,
                onSelect = { account ->
                    onChange(
                        filter.copy(account = account.takeUnless { it == "All accounts" })
                    )
                }
            )
        }
        OutlinedTextField(
            value = filter.merchantQuery,
            onValueChange = { onChange(filter.copy(merchantQuery = it)) },
            label = { Text("Merchant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (filter.preset == DatePreset.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "From (YYYY-MM-DD)",
                    value = filter.customStart,
                    onChange = { onChange(filter.copy(customStart = it)) },
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "To (YYYY-MM-DD)",
                    value = filter.customEnd,
                    onChange = { onChange(filter.copy(customEnd = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    value: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) {
                onChange(null)
            } else {
                runCatching { LocalDate.parse(input) }.getOrNull()?.let(onChange)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun TransactionRow(
    transaction: TransactionEntity,
    onDetails: () -> Unit,
    onCategorySelected: (Category) -> Unit
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onDetails)) {
            Text(transaction.merchant, style = MaterialTheme.typography.titleSmall)
            Text(
                formatter.format(Instant.ofEpochMilli(transaction.occurredAt)),
                style = MaterialTheme.typography.labelSmall
            )
            Text("Tap for source SMS", style = MaterialTheme.typography.labelSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatAmount(transaction.amountMinor, transaction.currency))
            Dropdown(
                label = Category.fromStorage(transaction.category).displayName,
                options = Category.entries.map { it.displayName },
                onSelect = { label ->
                    Category.entries.firstOrNull { it.displayName == label }
                        ?.let(onCategorySelected)
                }
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    transaction: TransactionEntity,
    repository: TransactionRepository,
    onDismiss: () -> Unit
) {
    var messages by remember { mutableStateOf<List<RawSmsEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())
    }

    LaunchedEffect(transaction.id) {
        thread {
            val loaded = repository.rawMessagesFor(transaction)
            Handler(Looper.getMainLooper()).post {
                messages = loaded
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Transaction source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Parsed amount: ${formatAmount(transaction.amountMinor, transaction.currency)}")
                Text("Parsed merchant: ${transaction.merchant}")
                Text("Parsed date: ${formatter.format(Instant.ofEpochMilli(transaction.occurredAt))}")
                Text("Sender: ${transaction.sourceAddress}")
                Text("Raw SMS near this date:", style = MaterialTheme.typography.titleSmall)
                if (loading) {
                    CircularProgressIndicator()
                } else if (messages.isEmpty()) {
                    Text("No stored SMS found for this sender and date.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(messages, key = { it.id }) { message ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    formatter.format(Instant.ofEpochMilli(message.date)),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(message.body, style = MaterialTheme.typography.bodySmall)
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun Dropdown(label: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}
