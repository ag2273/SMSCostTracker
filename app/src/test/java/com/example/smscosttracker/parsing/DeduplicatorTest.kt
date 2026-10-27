package com.example.smscosttracker.parsing

import com.example.smscosttracker.data.ParsedTransaction
import com.example.smscosttracker.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeduplicatorTest {
    private val now = 1_700_000_000_000L

    private fun candidate(
        amount: Long = 1000L,
        merchant: String = "Carrefour",
        time: Long = now
    ) = ParsedTransaction(
        amountMinor = amount,
        currency = "EGP",
        merchant = merchant,
        occurredAt = time,
        sourceAddress = "BANK"
    )

    private fun existing(
        amount: Long = 1000L,
        merchant: String = "Carrefour",
        time: Long = now
    ) = TransactionEntity(
        amountMinor = amount,
        currency = "EGP",
        merchant = merchant,
        occurredAt = time,
        sourceAddress = "BANK",
        category = "Uncategorized"
    )

    @Test
    fun `same amount merchant and time is a duplicate`() {
        assertTrue(Deduplicator.isDuplicate(candidate(), existing()))
    }

    @Test
    fun `same details within 24 hours is a duplicate`() {
        assertTrue(Deduplicator.isDuplicate(candidate(time = now), existing(time = now - 23 * 3600_000L)))
    }

    @Test
    fun `same details outside 24 hours is not a duplicate`() {
        assertFalse(Deduplicator.isDuplicate(candidate(time = now), existing(time = now - 25 * 3600_000L)))
    }

    @Test
    fun `different merchant is not a duplicate`() {
        assertFalse(Deduplicator.isDuplicate(candidate(merchant = "Carrefour"), existing(merchant = "STC")))
    }

    @Test
    fun `unknown merchant is never treated as a duplicate`() {
        assertFalse(Deduplicator.isDuplicate(candidate(merchant = "Unknown"), existing(merchant = "Unknown")))
    }

    @Test
    fun `batch dedupe keeps first occurrence only`() {
        val batch = listOf(candidate(), candidate(time = now + 60_000L))
        assertEquals(1, Deduplicator.dedupe(batch, emptyList()).size)
    }

    @Test
    fun `bucketed dedupe matches pairwise comparison and is fast on large histories`() {
        val random = java.util.Random(42)
        val merchants = listOf("Carrefour", "Panda", "Jarir", "Unknown", "", "STC")
        fun randomTime() = now + random.nextInt(60 * 24) * 60_000L * 30
        val existing = (1..10_000).map {
            existing(random.nextInt(500).toLong() * 100, merchants[random.nextInt(merchants.size)], randomTime())
        }
        val candidates = (1..10_000).map {
            candidate(random.nextInt(500).toLong() * 100, merchants[random.nextInt(merchants.size)], randomTime())
        }

        val start = System.nanoTime()
        val accepted = Deduplicator.dedupe(candidates, existing)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        val expected = mutableListOf<ParsedTransaction>()
        for (candidate in candidates) {
            val duplicate = existing.any { Deduplicator.isDuplicate(candidate, it) } ||
                expected.any { Deduplicator.isDuplicate(candidate, it.asEntity()) }
            if (!duplicate) expected += candidate
        }
        assertEquals(expected, accepted)
        assertTrue("dedupe took $elapsedMs ms", elapsedMs < 1_000)
    }

    private fun ParsedTransaction.asEntity() = existing(amountMinor, merchant, occurredAt)
}
