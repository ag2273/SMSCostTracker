package com.example.smscosttracker.rates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.smscosttracker.analysis.ExchangeRates
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.ExchangeRateRepository
import com.example.smscosttracker.data.OnboardingPreferences
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Downloads public exchange rates (ADR-0005). The request carries nothing about the user; SMS,
 * amounts and merchants never leave the phone.
 */
object ExchangeRateSync {
    private const val WORK_NAME = "exchange-rates"
    private const val TIMEOUT_MS = 10_000

    /** Free sources without an API key, tried in order. */
    private val SOURCES = listOf(
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
        "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json",
        "https://open.er-api.com/v6/latest/USD"
    )

    sealed interface Result {
        data class Updated(val changed: Int) : Result
        data object Unavailable : Result
    }

    /** Downloads and saves rates. Rates set by hand are kept unless [replaceManual]. */
    fun refresh(context: Context, replaceManual: Boolean): Result {
        val rates = download() ?: return Result.Unavailable
        val changed = ExchangeRateRepository(AppDatabase.get(context)).applyDownloaded(rates, replaceManual)
        return Result.Updated(changed)
    }

    private fun download(): Map<String, Double>? {
        for (source in SOURCES) {
            val rates = runCatching { ExchangeRates.parseUsdRates(get(source)) }.getOrNull()
            // Only a complete list is used, so one source never mixes with another.
            if (rates != null && rates.size == ExchangeRates.SUPPORTED.size - 1) return rates
        }
        return null
    }

    private fun get(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads once a day while the setting is on and the phone is online. */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        if (!OnboardingPreferences(context).autoUpdateRates) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ExchangeRateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )
    }
}

class ExchangeRateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val result = runCatching { ExchangeRateSync.refresh(applicationContext, replaceManual = false) }.getOrNull()
        return if (result is ExchangeRateSync.Result.Updated) Result.success() else Result.retry()
    }
}
