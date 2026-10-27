package com.example.smscosttracker.analysis

import com.example.smscosttracker.data.TransactionEntity
import com.example.smscosttracker.parsing.TransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionReviewTest {
    private fun transaction(
        merchant: String = "Jarir",
        confidence: Double = 0.9,
        account: String = "SAB 4432"
    ) = TransactionEntity(
        amountMinor = 1000,
        currency = "SAR",
        merchant = merchant,
        occurredAt = 0L,
        sourceAddress = "SAB",
        category = "Shopping",
        account = account,
        categoryConfidence = confidence
    )

    private val pending = setOf(TransactionParser.instrumentKey("SAB", "9981"))

    @Test
    fun `confident transaction with a known merchant and confirmed card needs no review`() {
        assertEquals(emptySet<ReviewReason>(), TransactionReview.reasons(transaction(), pending))
    }

    @Test
    fun `each reason is detected`() {
        assertEquals(
            setOf(ReviewReason.LOW_CONFIDENCE, ReviewReason.UNKNOWN_MERCHANT, ReviewReason.UNVERIFIED_INSTRUMENT),
            TransactionReview.reasons(transaction("Unknown", 0.4, "SAB 9981"), pending)
        )
    }

    @Test
    fun `unscored transactions are not flagged for confidence`() {
        assertEquals(emptySet<ReviewReason>(), TransactionReview.reasons(transaction(confidence = -1.0), pending))
        assertNull(TransactionReview.confidenceLabel(transaction(confidence = -1.0)))
        assertEquals("62% match", TransactionReview.confidenceLabel(transaction(confidence = 0.62)))
    }

    @Test
    fun `needs review filter keeps only flagged transactions`() {
        val clean = transaction()
        val lowConfidence = transaction(merchant = "Noon", confidence = 0.3)
        val unverified = transaction(merchant = "Panda", account = "SAB 9981")

        val filtered = TransactionFilters.apply(
            listOf(clean, lowConfidence, unverified),
            TransactionFilterState(needsReview = true),
            pendingInstrumentKeys = pending
        )

        assertEquals(listOf(lowConfidence, unverified), filtered)
    }
}
