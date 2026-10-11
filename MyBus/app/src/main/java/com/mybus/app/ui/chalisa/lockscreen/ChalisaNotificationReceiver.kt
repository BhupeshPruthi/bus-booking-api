package com.mybus.app.ui.chalisa.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Handles the lock-screen notification's buttons without unlocking the phone or opening the app. */
@AndroidEntryPoint
class ChalisaNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var counter: ChalisaLockScreenCounter

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // A slow network must not hold the broadcast past Android's limit (ANR).
                val finished = withTimeoutOrNull(ChalisaLockScreenCounter.BUTTON_TIMEOUT_MILLIS) {
                    when (intent.action) {
                        ChalisaLockScreenCounter.ACTION_COUNT -> counter.countRound()
                        ChalisaLockScreenCounter.ACTION_UNDO -> counter.undoLastTap()
                        ChalisaLockScreenCounter.ACTION_STOP -> counter.stop()
                    }
                    true
                }
                if (finished == null) counter.onTimedOut()
            } finally {
                pending.finish()
            }
        }
    }
}
