package dev.trivk.smartringer.data

import android.content.Context
import android.content.SharedPreferences
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

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

    private fun persist(schedules: List<RingerSchedule>) {
        val json = JSONArray().apply { schedules.forEach { put(it.toJson()) } }
        preferences.edit().putString(SCHEDULES_KEY, json.toString()).apply()
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
    }
}

