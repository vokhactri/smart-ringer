package dev.trivk.smartringer.schedule

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.trivk.smartringer.data.ScheduleRepository

class AutomationTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val repository = ScheduleRepository(this)
        repository.setAutomationEnabled(!repository.loadSettings().automationEnabled)
        RingerScheduler(this).reconcile(TriggerReason.TILE)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val enabled = ScheduleRepository(this).loadSettings().automationEnabled
        val exact = RingerScheduler(this).canScheduleExactAlarms()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Smart Ringer"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !enabled -> "Automation off"
                !exact -> "Exact alarm needed"
                else -> "Automation on"
            }
        }
        tile.updateTile()
    }
}

