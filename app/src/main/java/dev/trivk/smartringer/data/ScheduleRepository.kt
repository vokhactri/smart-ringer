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
            if (key == AUTOMATION_ENABLED_KEY || key == USE_24_HOUR_KEY) trySend(loadSettings())
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
    )

    fun setAutomationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(AUTOMATION_ENABLED_KEY, enabled).commit()
    }

    fun setUse24HourTime(enabled: Boolean) {
        preferences.edit().putBoolean(USE_24_HOUR_KEY, enabled).apply()
    }

    internal fun loadAppliedState(): AppliedState? {
        val ruleId = preferences.getString(APPLIED_RULE_KEY, null) ?: return null
        return AppliedState(
            ruleId = ruleId,
            previousRingerMode = preferences.getInt(PREVIOUS_RINGER_MODE_KEY, android.media.AudioManager.RINGER_MODE_NORMAL),
            previousInterruptionFilter = preferences.getInt(
                PREVIOUS_INTERRUPTION_FILTER_KEY,
                android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
            ),
        )
    }

    internal fun saveAppliedState(state: AppliedState) {
        preferences.edit()
            .putString(APPLIED_RULE_KEY, state.ruleId)
            .putInt(PREVIOUS_RINGER_MODE_KEY, state.previousRingerMode)
            .putInt(PREVIOUS_INTERRUPTION_FILTER_KEY, state.previousInterruptionFilter)
            .commit()
    }

    internal fun updateAppliedRule(ruleId: String) {
        preferences.edit().putString(APPLIED_RULE_KEY, ruleId).commit()
    }

    internal fun clearAppliedState() {
        preferences.edit()
            .remove(APPLIED_RULE_KEY)
            .remove(PREVIOUS_RINGER_MODE_KEY)
            .remove(PREVIOUS_INTERRUPTION_FILTER_KEY)
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
        private const val APPLIED_RULE_KEY = "applied_rule"
        private const val PREVIOUS_RINGER_MODE_KEY = "previous_ringer_mode"
        private const val PREVIOUS_INTERRUPTION_FILTER_KEY = "previous_interruption_filter"
    }
}

internal data class AppliedState(
    val ruleId: String,
    val previousRingerMode: Int,
    val previousInterruptionFilter: Int,
)
