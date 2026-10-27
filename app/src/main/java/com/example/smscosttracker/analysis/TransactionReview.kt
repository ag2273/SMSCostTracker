package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.parsing.TransactionParser

enum class ReviewReason { LOW_CONFIDENCE, UNKNOWN_MERCHANT, UNVERIFIED_INSTRUMENT }

object TransactionReview {
    const val CONFIDENCE_THRESHOLD = 0.6

    /** [pendingInstrumentKeys] are `TransactionParser.instrumentKey`s the user has not confirmed yet. */
    fun reasons(
        transaction: TransactionEntity,
        pendingInstrumentKeys: Set<String>
    ): Set<ReviewReason> = buildSet {
        val confidence = transaction.categoryConfidence
        if (confidence >= 0 && confidence < CONFIDENCE_THRESHOLD) add(ReviewReason.LOW_CONFIDENCE)
        if (transaction.merchant.isBlank() || transaction.merchant.equals("Unknown", ignoreCase = true)) {
            add(ReviewReason.UNKNOWN_MERCHANT)
        }
        if (hasPendingInstrument(transaction, pendingInstrumentKeys)) add(ReviewReason.UNVERIFIED_INSTRUMENT)
    }

    fun needsReview(transaction: TransactionEntity, pendingInstrumentKeys: Set<String>): Boolean =
        reasons(transaction, pendingInstrumentKeys).isNotEmpty()

    fun hasPendingInstrument(transaction: TransactionEntity, pendingInstrumentKeys: Set<String>): Boolean {
        val digits = transaction.account.substringAfterLast(' ').trim()
        if (digits.isEmpty()) return false
        return TransactionParser.instrumentKey(transaction.sourceAddress, digits) in pendingInstrumentKeys
    }

    /** "62% match" for a known confidence, null when the classifier has not scored it. */
    fun confidenceLabel(transaction: TransactionEntity): String? =
        transaction.categoryConfidence.takeIf { it >= 0 }?.let { "${Math.round(it * 100)}% match" }
}
