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

        /** Lock-screen buttons must finish well inside Android's background broadcast limit. */
        const val BUTTON_TIMEOUT_MILLIS = 9_000L
        const val TIMED_OUT_NOTE = "The connection is very slow. This round may not have been counted. Tap +1 again; it won't count twice."
    }

    private val notifications = NotificationManagerCompat.from(context)

    /** What the counter last showed, so a failed tap can add a note without more network calls. */
    @Volatile private var lastContent: LockScreenContent? = null

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

    /** The +1 counter (not the "ended" message) is on the lock screen right now. */
    fun isCounterShowing(): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .any { it.id == NOTIFICATION_ID && it.isOngoing }

    /**
     * Puts the counter on the lock screen while a marathon runs, unless the reader turned it off.
     * Called when the Chalisa screen shows a running marathon the reader has joined.
     */
    suspend fun showIfOn() {
        if (!store.isLockScreenCounterOn() || !canPostNotifications() || isCounterShowing()) return
        refresh()
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
        val marathonId = summary?.marathon?.id
        show(
            LockScreenContent(
                today = me?.today ?: summary?.myToday ?: 0,
                marathonCount = me?.marathons?.firstOrNull { it.id == marathonId }?.count ?: lastContent?.marathonCount ?: 0,
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

    /**
     * The +1 button (notification or Quick Settings tile). Returns a short line for the tile.
     * The notification is only drawn while the counter is on.
     */
    suspend fun countRound(): String {
        var outcome = "Round counted"
        repository.addRound()
            .onSuccess { counts ->
                counts.reading?.let { store.saveLastTap(it.clientRequestId, it.undoUntil) }
                if (store.isLockScreenCounterOn()) show(counts.toContent(canUndo = true, note = outcome))
            }
            .onFailure { outcome = handleFailure(it) }
        return outcome
    }

    /** A button's work ran out of time (very slow network): say so without more calls. */
    suspend fun onTimedOut() {
        if (store.isLockScreenCounterOn()) showNote(TIMED_OUT_NOTE)
    }

    /** The Undo button: removes the latest tap if it's still within the undo time. */
    suspend fun undoLastTap() {
        val (id, _) = store.lastTap() ?: return showNote("Nothing to undo")
        repository.undo(id)
            .onSuccess { counts ->
                store.clearLastTap()
                show(counts.toContent(canUndo = false, note = "Removed"))
            }
            .onFailure {
                if ((it as? ChalisaException)?.code == ChalisaErrorCodes.NETWORK) {
                    // Keep the tap so Undo can be tried again.
                    showNote("No connection, so nothing was removed. Tap Undo again.")
                } else {
                    store.clearLastTap()
                    handleFailure(it)
                }
            }
    }

    /** Shows what went wrong and returns it as a short line for the tile. */
    private suspend fun handleFailure(error: Throwable): String {
        val note = when ((error as? ChalisaException)?.code) {
            ChalisaErrorCodes.MARATHON_NOT_RUNNING -> return "No marathon".also { showEnded(null) }
            ChalisaErrorCodes.MARATHON_NOT_STARTED -> "Counting opens when the marathon starts."
                .also { showEnded(it); return "Not started" }
            ChalisaErrorCodes.NAME_REQUIRED, ChalisaErrorCodes.INVALID_GUEST_TOKEN ->
                "Open the app and enter your name to count"
            // The server may have counted it; the next +1 reuses the same id, so it can't count twice.
            ChalisaErrorCodes.NETWORK -> "No connection. This round may not have been counted. Tap +1 again; it won't count twice."
            else -> error.message ?: "Couldn't count this round. Please try again."
        }
        if (store.isLockScreenCounterOn()) showNote(note)
        return note
    }

    /** Adds a note to what's showing, using the last numbers instead of reloading them. */
    private suspend fun showNote(note: String) {
        val current = lastContent
        if (current != null) show(current.copy(note = note, canUndo = canUndoLastTap())) else refresh(note)
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
        lastContent = content.copy(note = null)
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

    /**
     * The marathon isn't running: say so, and let the reader swipe the notification away.
     * The reader's on/off choice is kept, so the counter comes back for the next marathon.
     */
    @SuppressLint("MissingPermission")
    private suspend fun showEnded(note: String?) {
        store.clearLastTap()
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
        if (isCounterShowing()) showEnded(null)
    }
}
