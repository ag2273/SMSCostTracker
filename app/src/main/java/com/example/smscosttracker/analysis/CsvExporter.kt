package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionChannel
import com.example.smscosttracker.data.TransactionEntity
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {
    const val HEADER = "Date,Time,Merchant,Category,Amount,Currency,Channel,Account,Source"

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun toCsv(
        transactions: List<TransactionEntity>,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val rows = transactions
            .sortedWith(
                compareByDescending<TransactionEntity> { it.occurredAt }
                    .thenByDescending { it.id }
            )
            .map { row(it, zone) }
        return (listOf(HEADER) + rows).joinToString("\n")
    }

    fun suggestedFileName(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return "sms-cost-tracker-${DATE.format(date)}.csv"
    }

    private fun row(transaction: TransactionEntity, zone: ZoneId): String {
        val instant = Instant.ofEpochMilli(transaction.occurredAt).atZone(zone)
        return listOf(
            DATE.format(instant.toLocalDate()),
            TIME.format(instant.toLocalTime()),
            transaction.merchant,
            Category.fromStorage(transaction.category).displayName,
            BigDecimal.valueOf(transaction.amountMinor, 2).toPlainString(),
            transaction.currency,
            channelLabel(transaction),
            transaction.account,
            transaction.sourceAddress
        ).joinToString(",") { escape(it) }
    }

    private fun channelLabel(transaction: TransactionEntity): String =
        when (TransactionChannel.fromStorage(transaction.channel)) {
            TransactionChannel.CARD -> "Card"
            TransactionChannel.ACCOUNT -> "Account"
            TransactionChannel.OTHER -> "Other"
        }

    private fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
