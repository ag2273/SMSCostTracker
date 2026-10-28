package com.example.smscosttracker.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.smscosttracker.alerts.SubscriptionAlerts
import com.example.smscosttracker.data.AppDatabase
import com.example.smscosttracker.data.ImportResult
import com.example.smscosttracker.data.OnboardingPreferences
import com.example.smscosttracker.data.TransactionImporter
import java.util.concurrent.TimeUnit

/** Imports new messages from the selected bank threads, on the device, without a manual re-import. */
object SmsSync {
    private const val WORK_NAME = "sms-sync"
    private const val OVERLAP_MS = 24L * 60 * 60 * 1000

    /** Returns null when sync is off, onboarding is unfinished or SMS access was revoked. */
    fun syncNow(context: Context): ImportResult? {
        val preferences = OnboardingPreferences(context)
        if (!preferences.complete || !preferences.autoSync) return null
        val threads = preferences.selectedThreadIds
        if (threads.isEmpty()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val database = AppDatabase.get(context)
        // Stored SMS are unique, so re-reading the last day only costs the duplicate inserts.
        val since = (database.rawSmsDao().latestDate() ?: 0L) - OVERLAP_MS
        val result = TransactionImporter(database)
            .import(SmsReader.readMessagesSince(context, threads, since))
        preferences.lastSyncedAt = System.currentTimeMillis()
        runCatching { SubscriptionAlerts.check(context) }
        return result
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SmsSyncWorker>(6, TimeUnit.HOURS).build()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

class SmsSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        runCatching { SmsSync.syncNow(applicationContext) }
        return Result.success()
    }
}
