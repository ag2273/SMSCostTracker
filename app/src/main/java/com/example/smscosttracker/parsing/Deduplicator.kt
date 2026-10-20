package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.ParsedTransaction
import com.example.smscosttracker.data.TransactionEntity
import kotlin.math.abs

object Deduplicator {
    const val WINDOW_MS = 24L * 60 * 60 * 1000

    fun isDuplicate(candidate: ParsedTransaction, existing: TransactionEntity): Boolean =
        candidate.key().matches(existing.key())

    fun dedupe(
        candidates: List<ParsedTransaction>,
        existing: List<TransactionEntity>
    ): List<ParsedTransaction> {
        val accepted = mutableListOf<ParsedTransaction>()
        for (candidate in candidates) {
            val duplicateOfExisting = existing.any { isDuplicate(candidate, it) }
            val duplicateOfBatch = accepted.any { candidate.key().matches(it.key()) }
            if (!duplicateOfExisting && !duplicateOfBatch) accepted += candidate
        }
        return accepted
    }

    private fun ParsedTransaction.key() = TransactionKey(
        amountMinor = amountMinor,
        merchant = merchant,
        occurredAt = occurredAt
    )

    private fun TransactionEntity.key() = TransactionKey(
        amountMinor = amountMinor,
        merchant = merchant,
        occurredAt = occurredAt
    )

    private data class TransactionKey(
        val amountMinor: Long,
        val merchant: String,
        val occurredAt: Long
    ) {
        val hasKnownMerchant: Boolean
            get() = merchant.isNotBlank() && !merchant.equals(UNKNOWN_MERCHANT, ignoreCase = true)

        fun matches(other: TransactionKey): Boolean =
            hasKnownMerchant &&
                other.hasKnownMerchant &&
                amountMinor == other.amountMinor &&
                merchant.equals(other.merchant, ignoreCase = true) &&
                abs(occurredAt - other.occurredAt) <= WINDOW_MS
    }

    private const val UNKNOWN_MERCHANT = "Unknown"
}
