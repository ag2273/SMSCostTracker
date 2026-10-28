package com.example.smscosttracker.ui.components

import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.smscosttracker.analysis.CsvExporter
import com.example.smscosttracker.analysis.OriginalAmount
import com.example.smscosttracker.data.TransactionEntity
import kotlin.concurrent.thread

@Composable
fun rememberCsvExport(onMessage: (String) -> Unit): (List<TransactionEntity>, Map<Long, OriginalAmount>) -> Unit {
    val context = LocalContext.current
    val messageHandler by rememberUpdatedState(onMessage)
    var pending by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var pendingOriginals by remember { mutableStateOf<Map<Long, OriginalAmount>>(emptyMap()) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val rows = pending
        val originals = pendingOriginals
        pending = emptyList()
        pendingOriginals = emptyMap()
        if (uri == null) return@rememberLauncherForActivityResult
        thread {
            val success = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    val bytes = ("\uFEFF" + CsvExporter.toCsv(rows, originals = originals)).toByteArray(Charsets.UTF_8)
                    stream.write(bytes)
                } ?: error("Could not open the selected file")
            }.isSuccess
            val message = if (success) "Exported ${rows.size} transactions" else "Export failed"
            Handler(Looper.getMainLooper()).post { messageHandler(message) }
        }
    }

    return { transactions, originals ->
        pending = transactions
        pendingOriginals = originals
        launcher.launch(CsvExporter.suggestedFileName())
    }
}
