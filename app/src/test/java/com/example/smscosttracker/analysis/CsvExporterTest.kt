package com.example.smscosttracker.analysis

import com.example.smscosttracker.classification.Category
import com.example.smscosttracker.data.TransactionEntity
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {
    private val zone = ZoneId.of("UTC")

    private fun transaction(
        id: Long = 0,
        merchant: String = "Carrefour",
        category: Category = Category.GROCERIES,
        amountMinor: Long = 124900,
        currency: String = "SAR",
        date: LocalDate = LocalDate.of(2026, 10, 5),
        hour: Int = 14,
        minute: Int = 30,
        channel: String = "CARD",
        account: String = "SAB 1214",
        sourceAddress: String = "SAB"
    ) = TransactionEntity(
        id = id,
        amountMinor = amountMinor,
        currency = currency,
        merchant = merchant,
        occurredAt = date.atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(minute.toLong())
            .toInstant().toEpochMilli(),
        sourceAddress = sourceAddress,
        category = category.displayName,
        account = account,
        channel = channel
    )

    @Test
    fun `header is the first line`() {
        val csv = CsvExporter.toCsv(emptyList(), zone)

        assertEquals(CsvExporter.HEADER, csv)
    }

    @Test
    fun `row contains parsed transaction fields`() {
        val csv = CsvExporter.toCsv(listOf(transaction()), zone)

        assertEquals(
            "Date,Time,Merchant,Category,Amount,Currency,Channel,Account,Source\n" +
                "2026-10-05,14:30,Carrefour,Groceries,1249.00,SAR,Card,SAB 1214,SAB",
            csv
        )
    }

    @Test
    fun `rows are ordered newest first`() {
        val older = transaction(id = 1, merchant = "Older", date = LocalDate.of(2026, 10, 1))
        val newer = transaction(id = 2, merchant = "Newer", date = LocalDate.of(2026, 10, 9))

        val lines = CsvExporter.toCsv(listOf(older, newer), zone).lines()

        assertTrue(lines[1].contains("Newer"))
        assertTrue(lines[2].contains("Older"))
    }

    @Test
    fun `fields with commas and quotes are escaped`() {
        val csv = CsvExporter.toCsv(
            listOf(transaction(merchant = "Jarir, \"Bookstore\"")),
            zone
        )

        val line = csv.lines()[1]
        assertTrue(line.contains("\"Jarir, \"\"Bookstore\"\"\""))
    }

    @Test
    fun `amount keeps two decimal places without floating point drift`() {
        val csv = CsvExporter.toCsv(listOf(transaction(amountMinor = 5)), zone)
        val amount = csv.lines()[1].split(",")[4]

        assertEquals("0.05", amount)
    }

    @Test
    fun `account channel is labelled`() {
        val csv = CsvExporter.toCsv(
            listOf(transaction(channel = "ACCOUNT", account = "SAB 001")),
            zone
        )

        val columns = csv.lines()[1].split(",")
        assertEquals("Account", columns[6])
        assertEquals("SAB 001", columns[7])
    }

    @Test
    fun `stored category names are normalised for display`() {
        val csv = CsvExporter.toCsv(
            listOf(transaction().copy(category = "SHOPPING")),
            zone
        )

        assertEquals("Shopping", csv.lines()[1].split(",")[3])
    }

    @Test
    fun `suggested file name is dated`() {
        val now = LocalDate.of(2026, 10, 21).atStartOfDay(zone).toInstant().toEpochMilli()

        assertEquals("sms-cost-tracker-2026-10-21.csv", CsvExporter.suggestedFileName(now, zone))
    }
}
