package com.example.smscosttracker.analysis

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Where a rate version came from. */
enum class RateSource(val label: String) {
    BUILT_IN("Built in"),
    ONLINE("Downloaded"),
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
    /** The currencies the app converts between. All use two decimals in minor units. */
    val SUPPORTED = listOf("USD", "SAR", "EGP", "EUR", "GBP")

    /** Starting rates, effective from the beginning of time, used until the first download or edit. */
    val BUILT_IN = listOf(
        RateVersion("SAR", 3.75, 0L, RateSource.BUILT_IN),
        RateVersion("EGP", 48.50, 0L, RateSource.BUILT_IN),
        RateVersion("EUR", 0.92, 0L, RateSource.BUILT_IN),
        RateVersion("GBP", 0.79, 0L, RateSource.BUILT_IN)
    )

    /** Changes smaller than this (0.01%) are not saved as a new version. */
    private const val MIN_CHANGE = 0.0001

    /** Whether [new] differs enough from [current] to become a new version. */
    fun changed(current: Double?, new: Double): Boolean =
        current == null || kotlin.math.abs(new - current) > current * MIN_CHANGE

    /**
     * Reads units-per-USD for the supported currencies from a downloaded rate list, either
     * `{"usd": {"sar": 3.75, …}}` or `{"rates": {"SAR": 3.75, …}}`. Missing or implausible values
     * are left out rather than guessed.
     */
    fun parseUsdRates(json: String): Map<String, Double> =
        SUPPORTED.filter { it != "USD" }.mapNotNull { code ->
            val match = Regex("\"${code}\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(json) ?: return@mapNotNull null
            val value = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            if (value > 0.0 && value < 1_000_000.0) code to value else null
        }.toMap()

    /**
     * The new versions a download makes, effective [now]. Rates the user set by hand are kept
     * unless [replaceManual] (the user asked to update everything), and unchanged rates are skipped.
     */
    fun updatesFrom(
        current: Map<String, RateVersion>,
        downloaded: Map<String, Double>,
        now: Long,
        replaceManual: Boolean
    ): List<RateVersion> = downloaded.mapNotNull { (currency, rate) ->
        val latest = current[currency]
        if (latest?.source == RateSource.MANUAL && !replaceManual) return@mapNotNull null
        if (!changed(latest?.unitsPerUsd, rate)) return@mapNotNull null
        RateVersion(currency, rate, now, RateSource.ONLINE)
    }

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
