package com.example.smscosttracker.data

import com.example.smscosttracker.analysis.CurrencyConverter
import com.example.smscosttracker.analysis.ExchangeRates
import com.example.smscosttracker.analysis.RateSource
import com.example.smscosttracker.analysis.RateVersion

/** Stored exchange rate versions. Every change is a new version, so earlier transactions keep their rate. */
class ExchangeRateRepository(private val database: AppDatabase) {
    fun versions(): List<RateVersion> {
        val dao = database.exchangeRateDao()
        val stored = dao.getAll()
        if (stored.isEmpty()) {
            dao.insertAll(ExchangeRates.BUILT_IN.map { it.toEntity() })
            return ExchangeRates.BUILT_IN
        }
        return stored.mapNotNull { it.toVersion() }
    }

    fun converter(): CurrencyConverter = CurrencyConverter(versions())

    /** Saves a rate the user typed; it applies from [now] and is kept by daily downloads. */
    fun setManual(currency: String, unitsPerUsd: Double, now: Long = System.currentTimeMillis()): Boolean {
        val current = converter().current()[currency]?.unitsPerUsd
        if (unitsPerUsd <= 0.0 || !ExchangeRates.changed(current, unitsPerUsd)) return false
        database.exchangeRateDao().insertAll(listOf(RateVersion(currency, unitsPerUsd, now, RateSource.MANUAL).toEntity()))
        return true
    }

    /** Saves downloaded rates as new versions from [now]; returns how many changed. */
    fun applyDownloaded(
        rates: Map<String, Double>,
        replaceManual: Boolean,
        now: Long = System.currentTimeMillis()
    ): Int {
        val updates = ExchangeRates.updatesFrom(converter().current(), rates, now, replaceManual)
        if (updates.isNotEmpty()) database.exchangeRateDao().insertAll(updates.map { it.toEntity() })
        return updates.size
    }

    private fun RateVersion.toEntity() = ExchangeRateEntity(
        currency = currency,
        unitsPerUsd = unitsPerUsd,
        effectiveFrom = effectiveFrom,
        source = source.name
    )

    private fun ExchangeRateEntity.toVersion(): RateVersion? {
        val parsed = RateSource.entries.firstOrNull { it.name == source } ?: return null
        return RateVersion(currency, unitsPerUsd, effectiveFrom, parsed)
    }
}
