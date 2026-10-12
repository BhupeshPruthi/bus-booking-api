package com.mybus.app.ui.chalisa.lockscreen

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mybus.app.data.repository.ChalisaRepository
import com.mybus.app.ui.chalisa.formatCount
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Quick Settings tile "Chalisa +1": swipe down, even on the lock screen, and tap to count a round. */
@AndroidEntryPoint
class ChalisaTileService : TileService() {

    @Inject lateinit var counter: ChalisaLockScreenCounter
    @Inject lateinit var repository: ChalisaRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val summary = repository.summary().getOrNull()
            val running = summary?.state == "running"
            render(
                state = if (running) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE,
                subtitle = when {
                    summary == null -> null
                    running -> "Today ${formatCount(summary.myToday)}"
                    else -> "No Path"
                }
            )
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            render(Tile.STATE_ACTIVE, "Counting…")
            val outcome = withTimeoutOrNull(ChalisaLockScreenCounter.BUTTON_TIMEOUT_MILLIS) { counter.countRound() }
            // Shows what happened ("Round counted", "Too soon", no connection) on the tile itself.
            render(Tile.STATE_ACTIVE, outcome ?: "Slow connection, tap again")
        }
    }

    private fun render(state: Int, subtitle: String?) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = "Chalisa +1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = subtitle
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
