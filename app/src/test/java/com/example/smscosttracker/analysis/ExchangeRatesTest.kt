package com.example.smscosttracker.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRatesTest {
    private val converter = CurrencyConverter(ExchangeRates.BUILT_IN)

    @Test
    fun `converts through USD in both directions`() {
        assertEquals(9_375L, converter.convert(2_500, "USD", "SAR", at = 0))
        assertEquals(2_500L, converter.convert(9_375, "SAR", "USD", at = 0))
        // SAR 100 → EGP at 48.50 / 3.75.
        assertEquals(129_333L, converter.convert(10_000, "SAR", "EGP", at = 0))
        assertEquals(1_234L, converter.convert(1_234, "sar", "SAR", at = 0))
    }

    @Test
    fun `each amount uses the version in effect at its time`() {
        val changedAt = 1_000_000L
        val converter = CurrencyConverter(
            ExchangeRates.BUILT_IN + RateVersion("EGP", 50.00, changedAt, RateSource.MANUAL)
        )

        assertEquals(485_000L, converter.convert(10_000, "USD", "EGP", at = changedAt - 1))
        assertEquals(500_000L, converter.convert(10_000, "USD", "EGP", at = changedAt))
        assertEquals(500_000L, converter.convert(10_000, "USD", "EGP", at = changedAt + 5))
    }

    @Test
    fun `a later change does not alter earlier conversions`() {
        val before = converter.convert(10_000, "EUR", "SAR", at = 500)
        val later = CurrencyConverter(ExchangeRates.BUILT_IN + RateVersion("EUR", 0.85, 1_000, RateSource.MANUAL))

        assertEquals(before, later.convert(10_000, "EUR", "SAR", at = 500))
        assertTrue(later.convert(10_000, "EUR", "SAR", at = 2_000)!! > before!!)
    }

    @Test
    fun `times before the first version use the earliest one`() {
        val converter = CurrencyConverter(listOf(RateVersion("SAR", 3.75, 5_000, RateSource.MANUAL)))
        assertEquals(375L, converter.convert(100, "USD", "SAR", at = 0))
    }

    @Test
    fun `a missing rate gives no conversion`() {
        assertNull(converter.convert(1_000, "KWD", "SAR", at = 0))
        assertNull(CurrencyConverter(emptyList()).convert(1_000, "USD", "SAR", at = 0))
    }

    @Test
    fun `current shows the latest version of each currency`() {
        val converter = CurrencyConverter(
            ExchangeRates.BUILT_IN + RateVersion("GBP", 0.80, 10, RateSource.MANUAL)
        )
        assertEquals(RateSource.MANUAL, converter.current().getValue("GBP").source)
        assertEquals(0.80, converter.current().getValue("GBP").unitsPerUsd, 0.0)
    }

    @Test
    fun `tiny changes are not new versions`() {
        assertTrue(ExchangeRates.changed(null, 3.75))
        assertFalse(ExchangeRates.changed(3.75, 3.75001))
        assertTrue(ExchangeRates.changed(48.5, 48.71))
    }
}
