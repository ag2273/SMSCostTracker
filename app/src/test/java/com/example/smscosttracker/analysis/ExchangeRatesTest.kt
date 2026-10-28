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
            ExchangeRates.BUILT_IN + RateVersion("EGP", 50.00, changedAt, RateSource.ONLINE)
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
        val converter = CurrencyConverter(listOf(RateVersion("SAR", 3.75, 5_000, RateSource.ONLINE)))
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
    fun `reads both download formats and skips bad values`() {
        val nested = """{"date":"2026-10-28","usd":{"aed":3.6725,"egp":48.71,"eur":0.9213,"gbp":0.7702,"sar":3.75,"usd":1}}"""
        assertEquals(
            mapOf("SAR" to 3.75, "EGP" to 48.71, "EUR" to 0.9213, "GBP" to 0.7702),
            ExchangeRates.parseUsdRates(nested)
        )

        val flat = """{"result":"success","base_code":"USD","rates":{"USD":1,"EGP":48.6,"SAR":3.75,"EUR":9.2e-1,"GBP":0}}"""
        assertEquals(mapOf("SAR" to 3.75, "EGP" to 48.6, "EUR" to 0.92), ExchangeRates.parseUsdRates(flat))

        assertTrue(ExchangeRates.parseUsdRates("<html>error</html>").isEmpty())
    }

    @Test
    fun `tiny changes are not new versions`() {
        assertTrue(ExchangeRates.changed(null, 3.75))
        assertFalse(ExchangeRates.changed(3.75, 3.75001))
        assertTrue(ExchangeRates.changed(48.5, 48.71))
    }

    @Test
    fun `a download keeps rates set by hand unless everything is updated`() {
        val current = CurrencyConverter(
            ExchangeRates.BUILT_IN + RateVersion("EGP", 50.0, 10, RateSource.MANUAL)
        ).current()
        val downloaded = mapOf("SAR" to 3.75, "EGP" to 48.71, "EUR" to 0.93)

        assertEquals(
            listOf(RateVersion("EUR", 0.93, 99, RateSource.ONLINE)),
            ExchangeRates.updatesFrom(current, downloaded, now = 99, replaceManual = false)
        )
        assertEquals(
            listOf("EGP", "EUR"),
            ExchangeRates.updatesFrom(current, downloaded, now = 99, replaceManual = true).map { it.currency }
        )
    }
}
