package com.example.smscosttracker.data

import android.content.Context
import com.example.smscosttracker.analysis.AlertType

/** Which subscription alerts the user turned on, and which alerts were already shown. */
class AlertPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sms_cost_tracker_alerts", Context.MODE_PRIVATE)

    /** Alerts are off until the user turns them on, which is when notification permission is asked. */
    fun isEnabled(type: AlertType): Boolean = prefs.getBoolean(key(type), false)

    fun setEnabled(type: AlertType, enabled: Boolean) {
        prefs.edit().putBoolean(key(type), enabled).apply()
    }

    val enabledTypes: Set<AlertType>
        get() = AlertType.entries.filter { isEnabled(it) }.toSet()

    /** Ids of alerts already shown, oldest first, so each event notifies once. */
    var shownIds: List<String>
        get() = prefs.getString(KEY_SHOWN, null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SHOWN, value.takeLast(MAX_SHOWN).joinToString("\n")).apply()
        }

    private fun key(type: AlertType) = "enabled_${type.name.lowercase()}"

    private companion object {
        const val KEY_SHOWN = "shown_ids"
        const val MAX_SHOWN = 500
    }
}
