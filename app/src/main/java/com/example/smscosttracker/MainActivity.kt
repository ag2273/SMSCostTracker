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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.TransactionImporter
import com.example.smscosttracker.sms.SmsReader
import com.example.smscosttracker.sms.SmsThread
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmsImportScreen()
                }
            }
        }
    }
}

@Composable
private fun SmsImportScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.get(context) }
    val importer = remember { TransactionImporter(database) }
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
        Text(
            "Imported messages stay on this device.",
            style = MaterialTheme.typography.bodySmall
        )

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
                        val total = database.transactionDao().count()
                        Handler(Looper.getMainLooper()).post {
                            loading = false
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
            Text(
                "${thread.messageCount} messages",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
