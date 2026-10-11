package com.mybus.app.ui.chalisa.lockscreen

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mybus.app.MainActivity
import com.mybus.app.R
import com.mybus.app.data.local.ChalisaStore
import com.mybus.app.data.remote.dto.ChalisaCounts
import com.mybus.app.data.repository.ChalisaErrorCodes
import com.mybus.app.data.repository.ChalisaException
import com.mybus.app.data.repository.ChalisaRepository
import com.mybus.app.ui.chalisa.formatCount
import com.mybus.app.ui.chalisa.formatLastRound
import com.mybus.app.ui.chalisa.parseInstant
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** What the lock-screen notification shows. */
data class LockScreenContent(
    val today: Int,
    val marathonCount: Long,
    val groupTotal: Long,
    val lastReadAt: String?,
    val canUndo: Boolean,
    val note: String? = null
)

/**
 * Counting from the lock screen: a quiet, persistent notification with +1, Undo and Stop.
 * Its buttons run in the background, so the phone stays locked and the screen can sleep
 * between rounds. The server still applies every rule (30 seconds, marathon running).
 */
@Singleton
class ChalisaLockScreenCounter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChalisaRepository,
    private val store: ChalisaStore
) {
    companion object {
        const val CHANNEL_ID = "chalisa_counter"
        const val NOTIFICATION_ID = 4108
        const val ACTION_COUNT = "com.mybus.app.chalisa.COUNT"
        const val ACTION_UNDO = "com.mybus.app.chalisa.UNDO"
        const val ACTION_STOP = "com.mybus.app.chalisa.STOP"
    }

    private val notifications = NotificationManagerCompat.from(context)

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Turns the counter on and shows the latest numbers. */
    suspend fun start() {
        store.setLockScreenCounter(true)
        refresh()
    }

    /** Removes the notification and remembers that the reader turned it off. */
    suspend fun stop() {
        store.setLockScreenCounter(false)
        notifications.cancel(NOTIFICATION_ID)
    }

    /** Re-reads the numbers from the server and redraws the notification, if it's on. */
    suspend fun refresh(note: String? = null) {
        if (!store.isLockScreenCounterOn()) return
        val summary = repository.summary().getOrNull()
        if (summary != null && summary.state != "running") {
            showEnded(if (summary.state == "scheduled") "Counting opens when the marathon starts." else null)
            return
        }
        val me = repository.myStats().getOrNull()
        val board = repository.board().getOrNull()
        show(
            LockScreenContent(
                today = me?.today ?: summary?.myToday ?: 0,
                marathonCount = board?.me?.count ?: 0,
                groupTotal = summary?.total ?: 0,
                lastReadAt = me?.lastReadAt,
                canUndo = canUndoLastTap(),
                note = note
            )
        )
    }

    /** Called after the app itself counts or undoes a round, to keep the notification in step. */
    suspend fun onCountsChanged(counts: ChalisaCounts) {
        counts.reading?.let { store.saveLastTap(it.clientRequestId, it.undoUntil) }
        if (!store.isLockScreenCounterOn()) return
        show(counts.toContent(canUndo = canUndoLastTap()))
    }

    /** The +1 button. */
    suspend fun countRound() {
        repository.addRound()
            .onSuccess { counts ->
                counts.reading?.let { store.saveLastTap(it.clientRequestId, it.undoUntil) }
                show(counts.toContent(canUndo = true, note = "Round counted"))
            }
            .onFailure { handleFailure(it) }
    }

    /** The Undo button: removes the latest tap if it's still within the undo time. */
    suspend fun undoLastTap() {
        val (id, _) = store.lastTap() ?: return refresh("Nothing to undo")
        repository.undo(id)
            .onSuccess { counts ->
                store.clearLastTap()
                show(counts.toContent(canUndo = false, note = "Removed"))
            }
            .onFailure {
                store.clearLastTap()
                handleFailure(it)
            }
    }

    private suspend fun handleFailure(error: Throwable) {
        when ((error as? ChalisaException)?.code) {
            ChalisaErrorCodes.MARATHON_NOT_RUNNING -> showEnded(null)
            ChalisaErrorCodes.MARATHON_NOT_STARTED -> showEnded("Counting opens when the marathon starts.")
            ChalisaErrorCodes.NAME_REQUIRED, ChalisaErrorCodes.INVALID_GUEST_TOKEN ->
                refresh("Open the app and enter your name to count")
            ChalisaErrorCodes.NETWORK -> refresh("No connection. This round wasn't counted, so tap +1 again.")
            else -> refresh(error.message)
        }
    }

    private suspend fun canUndoLastTap(): Boolean {
        val (_, until) = store.lastTap() ?: return false
        return parseInstant(until)?.isAfter(Instant.now()) == true
    }

    private fun ChalisaCounts.toContent(canUndo: Boolean, note: String? = null) = LockScreenContent(
        today = today,
        marathonCount = marathon?.myCount ?: 0,
        groupTotal = marathon?.total ?: 0,
        lastReadAt = lastReadAt,
        canUndo = canUndo,
        note = note
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Hanuman Chalisa counter", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Count rounds from the lock screen"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
        )
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, ChalisaNotificationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    @SuppressLint("MissingPermission")
    private fun show(content: LockScreenContent) {
        if (!canPostNotifications()) return
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = buildString {
            parseInstant(content.lastReadAt)?.let { append("Last round at ${formatLastRound(it)} · ") }
            append("Group ${formatCount(content.groupTotal)}")
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_chalisa)
            .setContentTitle("Today ${formatCount(content.today)} · Marathon ${formatCount(content.marathonCount)}")
            .setContentText(content.note ?: detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(content.note, detail).joinToString("\n")))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .addAction(0, "+1 Round", actionIntent(ACTION_COUNT, 1))
        if (content.canUndo) builder.addAction(0, "Undo", actionIntent(ACTION_UNDO, 2))
        builder.addAction(0, "Stop", actionIntent(ACTION_STOP, 3))
        notifications.notify(NOTIFICATION_ID, builder.build())
    }

    /** The marathon isn't running: say so, and let the reader swipe the notification away. */
    @SuppressLint("MissingPermission")
    private suspend fun showEnded(note: String?) {
        store.clearLastTap()
        if (note == null) store.setLockScreenCounter(false)
        if (!canPostNotifications()) return
        ensureChannel()
        notifications.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_chalisa)
                .setContentTitle(if (note == null) "The marathon has ended" else "Hanuman Chalisa")
                .setContentText(note ?: "Thank you for taking part. Your readings are in your Profile.")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(note != null)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .apply { if (note != null) addAction(0, "Stop", actionIntent(ACTION_STOP, 3)) }
                .build()
        )
    }

    /** Home found no running marathon: clear a counter that's still showing. */
    suspend fun onNoMarathonRunning() {
        if (store.isLockScreenCounterOn()) showEnded(null)
    }
}
