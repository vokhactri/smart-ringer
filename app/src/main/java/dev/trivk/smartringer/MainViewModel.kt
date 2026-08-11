package dev.trivk.smartringer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.trivk.smartringer.data.ScheduleRepository
import dev.trivk.smartringer.model.RingerSchedule
import dev.trivk.smartringer.schedule.RingerScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScheduleRepository(application)
    private val scheduler = RingerScheduler(application)

    val schedules = repository.schedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.load(),
    )

    fun save(schedule: RingerSchedule) {
        repository.save(schedule)
        scheduler.rescheduleAll(repository.load())
        scheduler.applyCurrentState(repository.load())
    }

    fun toggle(schedule: RingerSchedule) = save(schedule.copy(enabled = !schedule.enabled))

    fun delete(schedule: RingerSchedule) {
        scheduler.cancel(schedule)
        repository.delete(schedule.id)
        scheduler.rescheduleAll(repository.load())
        scheduler.applyCurrentState(repository.load())
    }

    fun refreshSchedules() = scheduler.rescheduleAll(repository.load())
}

