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
    }
}
