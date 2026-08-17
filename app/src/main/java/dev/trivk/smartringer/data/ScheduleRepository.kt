package dev.trivk.smartringer.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import dev.trivk.smartringer.model.AppSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

@SuppressLint("ApplySharedPref")
class ScheduleRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val schedules: Flow<List<RingerSchedule>> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SCHEDULES_KEY) trySend(load())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(load())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val timer: Flow<RingerTimer?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == TIMER_KEY) trySend(loadTimer())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadTimer())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settings: Flow<AppSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AUTOMATION_ENABLED_KEY || key == USE_24_HOUR_KEY || key == SETUP_PROMPT_DISMISSED_KEY) {
                trySend(loadSettings())
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadSettings())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun load(): List<RingerSchedule> {
        val raw = preferences.getString(SCHEDULES_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { add(array.getJSONObject(it).toSchedule()) }
            }
        }.getOrDefault(emptyList())
    }

    fun save(schedule: RingerSchedule) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == schedule.id }
        if (index >= 0) current[index] = schedule else current.add(schedule)
        persist(current)
    }

    fun delete(id: String) = persist(load().filterNot { it.id == id })

    fun loadTimer(): RingerTimer? {
        val raw = preferences.getString(TIMER_KEY, null) ?: return null
        return runCatching {
            JSONObject(raw).let {
                RingerTimer(
                    id = it.getString("id"),
                    mode = RingerMode.valueOf(it.getString("mode")),
                    startedAtMillis = it.getLong("startedAtMillis"),
                    endsAtMillis = it.getLong("endsAtMillis"),
                )
            }
        }.getOrNull()
    }

    fun saveTimer(timer: RingerTimer?) {
        val editor = preferences.edit()
        if (timer == null) {
            editor.remove(TIMER_KEY)
        } else {
            editor.putString(
                TIMER_KEY,
                JSONObject().apply {
                    put("id", timer.id)
                    put("mode", timer.mode.name)
                    put("startedAtMillis", timer.startedAtMillis)
                    put("endsAtMillis", timer.endsAtMillis)
                }.toString(),
            )
        }
        editor.commit()
    }

    fun loadSettings() = AppSettings(
        automationEnabled = preferences.getBoolean(AUTOMATION_ENABLED_KEY, true),
        use24HourTime = preferences.getBoolean(USE_24_HOUR_KEY, false),
        setupPromptDismissed = preferences.getBoolean(SETUP_PROMPT_DISMISSED_KEY, false),
    )

    fun setAutomationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(AUTOMATION_ENABLED_KEY, enabled).commit()
    }

    fun setUse24HourTime(enabled: Boolean) {
        preferences.edit().putBoolean(USE_24_HOUR_KEY, enabled).apply()
    }

    fun setSetupPromptDismissed(dismissed: Boolean) {
        preferences.edit().putBoolean(SETUP_PROMPT_DISMISSED_KEY, dismissed).apply()
    }

    internal fun loadActiveNotification(): ActiveNotificationState = ActiveNotificationState(
        postedKey = preferences.getString(ACTIVE_NOTIFICATION_POSTED_KEY, null),
        dismissedKey = preferences.getString(ACTIVE_NOTIFICATION_DISMISSED_KEY, null),
    )

    internal fun saveActiveNotificationPosted(key: String) {
        preferences.edit()
            .putString(ACTIVE_NOTIFICATION_POSTED_KEY, key)
            .remove(ACTIVE_NOTIFICATION_DISMISSED_KEY)
            .commit()
    }

    internal fun saveActiveNotificationDismissed(key: String) {
        preferences.edit().putString(ACTIVE_NOTIFICATION_DISMISSED_KEY, key).commit()
    }

    internal fun clearActiveNotification() {
        preferences.edit()
            .remove(ACTIVE_NOTIFICATION_POSTED_KEY)
            .remove(ACTIVE_NOTIFICATION_DISMISSED_KEY)
            .commit()
    }

    internal fun loadAppliedState(): AppliedState? {
        val ruleId = preferences.getString(APPLIED_RULE_KEY, null) ?: return null
        return AppliedState(
            ruleId = ruleId,
            // Installs that predate override tracking have no occurrence stored. An empty string
            // never matches a real occurrence, so the automation is simply applied again.
            occurrence = preferences.getString(APPLIED_OCCURRENCE_KEY, "").orEmpty(),
            previousRingerMode = preferences.getInt(PREVIOUS_RINGER_MODE_KEY, android.media.AudioManager.RINGER_MODE_NORMAL),
            previousInterruptionFilter = preferences.getInt(
                PREVIOUS_INTERRUPTION_FILTER_KEY,
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
            ),
            appliedRingerMode = preferences.getInt(APPLIED_RINGER_MODE_KEY, android.media.AudioManager.RINGER_MODE_NORMAL),
            appliedInterruptionFilter = preferences.getInt(
                APPLIED_INTERRUPTION_FILTER_KEY,
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
            ),
            overridden = preferences.getBoolean(APPLIED_OVERRIDDEN_KEY, false),
        )
    }

    internal fun saveAppliedState(state: AppliedState) {
        preferences.edit()
            .putString(APPLIED_RULE_KEY, state.ruleId)
            .putString(APPLIED_OCCURRENCE_KEY, state.occurrence)
            .putInt(PREVIOUS_RINGER_MODE_KEY, state.previousRingerMode)
            .putInt(PREVIOUS_INTERRUPTION_FILTER_KEY, state.previousInterruptionFilter)
            .putInt(APPLIED_RINGER_MODE_KEY, state.appliedRingerMode)
            .putInt(APPLIED_INTERRUPTION_FILTER_KEY, state.appliedInterruptionFilter)
            .putBoolean(APPLIED_OVERRIDDEN_KEY, state.overridden)
            .commit()
    }

    internal fun clearAppliedState() {
        preferences.edit()
            .remove(APPLIED_RULE_KEY)
            .remove(APPLIED_OCCURRENCE_KEY)
            .remove(PREVIOUS_RINGER_MODE_KEY)
            .remove(PREVIOUS_INTERRUPTION_FILTER_KEY)
            .remove(APPLIED_RINGER_MODE_KEY)
            .remove(APPLIED_INTERRUPTION_FILTER_KEY)
            .remove(APPLIED_OVERRIDDEN_KEY)
            .commit()
    }

    private fun persist(schedules: List<RingerSchedule>) {
        val json = JSONArray().apply { schedules.forEach { put(it.toJson()) } }
        preferences.edit().putString(SCHEDULES_KEY, json.toString()).commit()
    }

    private fun RingerSchedule.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("days", JSONArray(days.map(DayOfWeek::name)))
        put("mode", mode.name)
        put("startTime", startTime.toString())
        put("endTime", endTime.toString())
        put("enabled", enabled)
    }

    private fun JSONObject.toSchedule(): RingerSchedule {
        val dayArray = getJSONArray("days")
        val days = buildSet {
            repeat(dayArray.length()) { add(DayOfWeek.valueOf(dayArray.getString(it))) }
        }
        return RingerSchedule(
            id = getString("id"),
            name = getString("name"),
            days = days,
            mode = RingerMode.valueOf(getString("mode")),
            startTime = LocalTime.parse(getString("startTime")),
            endTime = LocalTime.parse(getString("endTime")),
            enabled = optBoolean("enabled", true),
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "smart_ringer"
        private const val SCHEDULES_KEY = "schedules"
        private const val TIMER_KEY = "timer"
        private const val AUTOMATION_ENABLED_KEY = "automation_enabled"
        private const val USE_24_HOUR_KEY = "use_24_hour"
        private const val SETUP_PROMPT_DISMISSED_KEY = "setup_prompt_dismissed"
        private const val APPLIED_RULE_KEY = "applied_rule"
        private const val APPLIED_OCCURRENCE_KEY = "applied_occurrence"
        private const val APPLIED_RINGER_MODE_KEY = "applied_ringer_mode"
        private const val APPLIED_INTERRUPTION_FILTER_KEY = "applied_interruption_filter"
        private const val APPLIED_OVERRIDDEN_KEY = "applied_overridden"
        private const val PREVIOUS_RINGER_MODE_KEY = "previous_ringer_mode"
        private const val PREVIOUS_INTERRUPTION_FILTER_KEY = "previous_interruption_filter"
        private const val ACTIVE_NOTIFICATION_POSTED_KEY = "active_notification_posted"
        private const val ACTIVE_NOTIFICATION_DISMISSED_KEY = "active_notification_dismissed"
    }
}

/**
 * What the app did to the ringer, and what it expects to still find.
 *
 * [previousRingerMode] / [previousInterruptionFilter] are the pre-automation state to restore, kept
 * from the first automation in a back-to-back chain. [appliedRingerMode] /
 * [appliedInterruptionFilter] are read back straight after applying, so a later reconcile can tell
 * whether the ringer still holds what the app left there. [occurrence] scopes [overridden] to a
 * single run, so a manual change today does not disable the same schedule tomorrow.
 *
 * [ruleId] is what marks the state as present on disk. Nothing branches on its value, but it must
 * keep being written: an install upgrading mid-run has no [occurrence] stored yet, and reading the
 * state back through [ruleId] is what preserves its pre-automation snapshot across the upgrade.
 */
internal data class AppliedState(
    val ruleId: String,
    val occurrence: String,
    val previousRingerMode: Int,
    val previousInterruptionFilter: Int,
    val appliedRingerMode: Int,
    val appliedInterruptionFilter: Int,
    val overridden: Boolean,
)

/**
 * [postedKey] is the automation occurrence the ongoing notification was last posted for,
 * [dismissedKey] the one the user swiped away. Both are compared against the current
 * occurrence so a reconcile never re-posts a notification the user already dealt with.
 */
internal data class ActiveNotificationState(
    val postedKey: String?,
    val dismissedKey: String?,
)
