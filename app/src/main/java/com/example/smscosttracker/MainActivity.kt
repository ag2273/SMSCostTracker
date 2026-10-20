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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.core.content.ContextCompat
import com.example.smscosttracker.analysis.DatePreset
import com.example.smscosttracker.analysis.TransactionFilterState
import com.example.smscosttracker.analysis.TransactionFilters
import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.sms.SmsReader
import com.example.smscosttracker.sms.SmsThread
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

@Composable
private fun App() {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val importer = remember { TransactionImporter(database) }
    val repository = remember { TransactionRepository(database) }
    var tab by remember { mutableStateOf(0) }
    var refreshKey by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Import") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Transactions") })
        }
        when (tab) {
            0 -> ImportScreen(
                importer = importer,
                repository = repository,
                onImported = { refreshKey++ }
            )
            else -> TransactionsScreen(repository = repository, refreshKey = refreshKey)
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

@Composable
private fun TransactionsScreen(repository: TransactionRepository, refreshKey: Int) {
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var filter by remember { mutableStateOf(TransactionFilterState()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(refreshKey) {
        thread {
            val loaded = repository.transactions()
            Handler(Looper.getMainLooper()).post {
                transactions = loaded
                loading = false
            }
        }
    }

    val filtered = TransactionFilters.apply(transactions, filter)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        FilterBar(filter = filter, onChange = { filter = it })
        if (loading) {
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
                        onCategorySelected = { category ->
                            thread {
                                repository.updateCategory(transaction, category)
                                val loaded = repository.transactions()
                                Handler(Looper.getMainLooper()).post {
                                    transactions = loaded
                                }
                            }
                        }
                    )
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    filter: TransactionFilterState,
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
    onCategorySelected: (Category) -> Unit
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.merchant, style = MaterialTheme.typography.titleSmall)
            Text(
                formatter.format(Instant.ofEpochMilli(transaction.occurredAt)),
                style = MaterialTheme.typography.labelSmall
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%.2f %s".format(transaction.amountMinor / 100.0, transaction.currency))
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
