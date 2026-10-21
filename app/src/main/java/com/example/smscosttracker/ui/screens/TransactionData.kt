package com.example.smscosttracker.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.data.TransactionRepository
import kotlin.concurrent.thread

data class TransactionListState(
    val transactions: List<TransactionEntity>,
    val loading: Boolean
)

@Composable
fun rememberTransactions(
    repository: TransactionRepository,
    refreshKey: Int
): TransactionListState {
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
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
    return TransactionListState(transactions, loading)
}

@Composable
fun rememberPendingCount(
    repository: TransactionRepository,
    refreshKey: Int
): Int {
    var pending by remember { mutableStateOf(0) }
    LaunchedEffect(refreshKey) {
        thread {
            val count = repository.pendingInstruments().size
            Handler(Looper.getMainLooper()).post { pending = count }
        }
    }
    return pending
}
