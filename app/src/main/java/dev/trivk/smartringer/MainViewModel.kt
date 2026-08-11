package dev.trivk.smartringer

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.trivk.smartringer.data.ScheduleRepository
import dev.trivk.smartringer.model.RingerMode
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.model.RingerTimer
import dev.trivk.smartringer.schedule.AutomationNotifications
import dev.trivk.smartringer.schedule.AutomationTileService
import dev.trivk.smartringer.schedule.RingerScheduler
import dev.trivk.smartringer.schedule.TriggerReason
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Duration

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScheduleRepository(application)
    private val scheduler = RingerScheduler(application)

    val schedules = repository.schedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.load(),
    )
    val timer = repository.timer.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.loadTimer(),
    )
    val settings = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.loadSettings(),
    )

    init {
        AutomationNotifications(application)
        scheduler.reconcile(TriggerReason.APP_RESUME)
    }

    fun save(schedule: RingerSchedule) {
        repository.save(schedule)
        scheduler.reconcile(TriggerReason.USER)
    }

    fun toggle(schedule: RingerSchedule) = save(schedule.copy(enabled = !schedule.enabled))

    fun delete(schedule: RingerSchedule) {
        scheduler.cancel(schedule)
        repository.delete(schedule.id)
        scheduler.reconcile(TriggerReason.USER)
    }

    fun startTimer(duration: Duration, mode: RingerMode) {
        val now = System.currentTimeMillis()
        repository.setAutomationEnabled(true)
        repository.saveTimer(
            RingerTimer(
                mode = mode,
                startedAtMillis = now,
                endsAtMillis = now + duration.toMillis(),
            ),
        )
        scheduler.reconcile(TriggerReason.USER)
        refreshTile()
    }

    fun cancelTimer() {
        scheduler.cancelTimer()
        repository.saveTimer(null)
        scheduler.reconcile(TriggerReason.USER)
    }

    fun setAutomationEnabled(enabled: Boolean) {
        repository.setAutomationEnabled(enabled)
        scheduler.reconcile(TriggerReason.USER)
        refreshTile()
    }

    fun setUse24HourTime(enabled: Boolean) = repository.setUse24HourTime(enabled)

    fun reconcile() = scheduler.reconcile(TriggerReason.APP_RESUME)

    private fun refreshTile() {
        TileService.requestListeningState(
            getApplication(),
            ComponentName(getApplication<Application>(), AutomationTileService::class.java),
        )
    }
}
