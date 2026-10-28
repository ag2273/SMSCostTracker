package com.example.smscosttracker.analysis

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Where a rate version came from. */
enum class RateSource(val label: String) {
    BUILT_IN("Built in"),
    MANUAL("Set by you")
}

/**
 * One exchange rate as [unitsPerUsd] (for SAR, 3.75), effective from [effectiveFrom] (epoch
 * millis) until a later version of the same currency replaces it.
 */
data class RateVersion(
    val currency: String,
    val unitsPerUsd: Double,
    val effectiveFrom: Long,
    val source: RateSource
)

object ExchangeRates {
    /** The currencies a card, an account or every screen can be shown in. */
    val MAIN = listOf("USD", "SAR", "EGP", "EUR", "GBP")

    /**
     * Every currency with a rate, so purchases in them convert too. Amounts are stored with two
     * decimals in minor units for all of them, KWD included.
     */
    val SUPPORTED = MAIN + listOf("KWD", "AED", "INR", "TRY")

    /** Rates that ship with the app, effective from the beginning of time, until the user sets one. */
    val BUILT_IN = listOf(
        RateVersion("SAR", 3.75, 0L, RateSource.BUILT_IN),
        RateVersion("EGP", 48.50, 0L, RateSource.BUILT_IN),
        RateVersion("EUR", 0.92, 0L, RateSource.BUILT_IN),
        RateVersion("GBP", 0.79, 0L, RateSource.BUILT_IN),
        RateVersion("KWD", 0.307, 0L, RateSource.BUILT_IN),
        RateVersion("AED", 3.6725, 0L, RateSource.BUILT_IN),
        RateVersion("INR", 88.0, 0L, RateSource.BUILT_IN),
        RateVersion("TRY", 42.0, 0L, RateSource.BUILT_IN)
    )

    /** Changes smaller than this (0.01%) are not saved as a new version. */
    private const val MIN_CHANGE = 0.0001

    /** Whether [new] differs enough from [current] to become a new version. */
    fun changed(current: Double?, new: Double): Boolean =
        current == null || kotlin.math.abs(new - current) > current * MIN_CHANGE

    fun format(unitsPerUsd: Double): String = String.format(Locale.US, "%.4f", unitsPerUsd)
}

/** Converts amounts between currencies with the rate version in effect at each amount's time. */
class CurrencyConverter(versions: List<RateVersion>) {
    private val byCurrency: Map<String, List<RateVersion>> = versions
        .groupBy { it.currency.uppercase(Locale.ROOT) }
        .mapValues { (_, list) -> list.sortedBy { it.effectiveFrom } }

    /** Units per USD for [currency] at [at]; the earliest version covers times before any version. */
    fun unitsPerUsd(currency: String, at: Long): Double? {
        val code = currency.uppercase(Locale.ROOT)
        if (code == "USD") return 1.0
        val list = byCurrency[code] ?: return null
        return (list.lastOrNull { it.effectiveFrom <= at } ?: list.first()).unitsPerUsd
    }

    /** The latest version of each currency, for showing and editing. */
    fun current(): Map<String, RateVersion> = byCurrency.mapValues { it.value.last() }

    /** [amountMinor] in [from] converted to [to] at [at], or null when a rate is missing. */
    fun convert(amountMinor: Long, from: String, to: String, at: Long): Long? {
        if (from.equals(to, ignoreCase = true)) return amountMinor
        val fromRate = unitsPerUsd(from, at) ?: return null
        val toRate = unitsPerUsd(to, at) ?: return null
        return BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(toRate))
            .divide(BigDecimal.valueOf(fromRate), 0, RoundingMode.HALF_UP)
            .toLong()
    }
}
