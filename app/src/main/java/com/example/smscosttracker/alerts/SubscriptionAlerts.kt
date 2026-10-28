package com.example.smscosttracker.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.smscosttracker.MainActivity
import com.example.smscosttracker.R
import com.example.smscosttracker.analysis.AlertType
import com.example.smscosttracker.analysis.AmountPattern
import com.example.smscosttracker.analysis.RecurringAlert
import com.example.smscosttracker.analysis.RecurringAlerts
import com.example.smscosttracker.data.AlertPreferences
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.TransactionRepository
import com.example.smscosttracker.ui.components.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Local notifications for confirmed subscriptions: late payments, price changes and payments due
 * tomorrow. Checked after each sync and once a day; nothing leaves the device.
 */
object SubscriptionAlerts {
    const val EXTRA_SERIES_KEY = "com.example.smscosttracker.SERIES_KEY"
    private const val CHANNEL_ID = "subscription-alerts"
    private const val WORK_NAME = "subscription-alerts"
    private val DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** Raises the alerts that are due and were not shown before. Returns how many were posted. */
    fun check(context: Context, today: LocalDate = LocalDate.now()): Int {
        val preferences = AlertPreferences(context)
        val enabled = preferences.enabledTypes
        if (enabled.isEmpty() || !OnboardingPreferences(context).complete) return 0
        val repository = TransactionRepository(AppDatabase.get(context))
        val shown = preferences.shownIds
        val fresh = RecurringAlerts.due(repository.recurringSeries(), today, enabled)
            .filter { it.id !in shown }
        if (fresh.isEmpty()) return 0
        val posted = fresh.filter { post(context, it) }
        preferences.shownIds = shown + posted.map { it.id }
        return posted.size
    }

    /** Runs [check] once a day while any alert is on, so alerts work without automatic sync. */
    fun schedule(context: Context) {
        if (AlertPreferences(context).enabledTypes.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            return
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SubscriptionAlertWorker>(1, TimeUnit.DAYS).build()
        )
    }

    fun permissionNeeded(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, alert: RecurringAlert): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        createChannel(context)
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SERIES_KEY, alert.series.key)
        val pending = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val (title, text) = message(alert)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(alert.id.hashCode(), notification)
        return true
    }

    private fun message(alert: RecurringAlert): Pair<String, String> {
        val series = alert.series
        val amount = formatMoney(series.typicalMinor, series.currency)
        val about = if (series.amountPattern == AmountPattern.VARIABLE) "about $amount" else amount
        return when (alert.type) {
            AlertType.LATE -> "${series.merchant} payment is late" to
                "Expected $about on ${alert.date.format(DATE)}. No payment seen since."
            AlertType.PRICE_CHANGED -> {
                val change = series.priceChange
                val from = change?.let { formatMoney(it.fromMinor, series.currency) } ?: amount
                val to = change?.let { formatMoney(it.toMinor, series.currency) } ?: amount
                val direction = if ((change?.toMinor ?: 0L) >= (change?.fromMinor ?: 0L)) "went up" else "went down"
                "${series.merchant} price $direction" to "Charged $to on ${alert.date.format(DATE)}, usually $from."
            }
            AlertType.DUE_SOON -> "${series.merchant} is due tomorrow" to
                "${about.replaceFirstChar { it.uppercase() }} expected on ${alert.date.format(DATE)}."
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Subscription alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Late payments, price changes and payments due tomorrow"
            }
        )
    }
}

class SubscriptionAlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runCatching { SubscriptionAlerts.check(applicationContext) }
        return Result.success()
    }
}
