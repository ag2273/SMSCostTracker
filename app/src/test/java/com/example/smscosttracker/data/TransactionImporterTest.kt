package com.example.smscosttracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionImporterTest {
    private fun sms(body: String) = RawSms("SAB", body, 0L, 1L)

    @Test
    fun `only messages that were actually inserted are parsed`() {
        val messages = listOf(sms("old"), sms("new"), sms("also old"), sms("also new"))

        val fresh = TransactionImporter.newlyInserted(messages, listOf(-1L, 41L, -1L, 42L))

        assertEquals(listOf(sms("new"), sms("also new")), fresh)
    }

    @Test
    fun `re-importing unchanged threads parses nothing`() {
        val messages = listOf(sms("a"), sms("b"))

        assertEquals(emptyList<RawSms>(), TransactionImporter.newlyInserted(messages, listOf(-1L, -1L)))
    }
}
