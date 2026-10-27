package com.example.smscosttracker.data

import android.content.Context

enum class OnboardingStage {
    WELCOME,
    PERMISSION,
    THREADS,
    SCAN,
    REVIEW,
    COMPLETE
}

class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sms_cost_tracker_onboarding", Context.MODE_PRIVATE)

    var stage: OnboardingStage
        get() = runCatching {
            OnboardingStage.valueOf(
                prefs.getString(KEY_STAGE, OnboardingStage.WELCOME.name)
                    ?: OnboardingStage.WELCOME.name
            )
        }.getOrDefault(OnboardingStage.WELCOME)
        set(value) {
            prefs.edit().putString(KEY_STAGE, value.name).apply()
        }

    var complete: Boolean
        get() = prefs.getBoolean(KEY_COMPLETE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COMPLETE, value).apply()
        }

    var selectedThreadIds: Set<Long>
        get() = prefs.getStringSet(KEY_THREADS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        set(value) {
            prefs.edit()
                .putStringSet(KEY_THREADS, value.map { it.toString() }.toSet())
                .apply()
        }

    var parserVersion: Int
        get() = prefs.getInt(KEY_PARSER_VERSION, 0)
        set(value) {
            prefs.edit().putInt(KEY_PARSER_VERSION, value).apply()
        }

    var autoSync: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_SYNC, value).apply()
        }

    var lastSyncedAt: Long
        get() = prefs.getLong(KEY_LAST_SYNCED, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SYNCED, value).apply()
        }

    fun markComplete(stage: OnboardingStage = OnboardingStage.COMPLETE) {
        prefs.edit()
            .putBoolean(KEY_COMPLETE, true)
            .putString(KEY_STAGE, stage.name)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_STAGE = "stage"
        const val KEY_COMPLETE = "complete"
        const val KEY_THREADS = "thread_ids"
        const val KEY_PARSER_VERSION = "parser_version"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_LAST_SYNCED = "last_synced_at"
    }
}
