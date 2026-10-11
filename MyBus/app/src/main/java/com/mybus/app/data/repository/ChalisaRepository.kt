package com.mybus.app.data.repository

import com.mybus.app.data.local.ChalisaStore
import com.mybus.app.data.remote.ApiService
import com.mybus.app.data.remote.dto.*
import com.mybus.app.data.remote.networkErrorMessage
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A failed Chalisa call, with the server's error code so screens can react to it. */
class ChalisaException(
    val code: String?,
    message: String,
    val retryAfterSeconds: Int? = null
) : Exception(message)

object ChalisaErrorCodes {
    const val ROUND_TOO_SOON = "ROUND_TOO_SOON"
    const val NAME_REQUIRED = "NAME_REQUIRED"
    const val INVALID_GUEST_TOKEN = "INVALID_GUEST_TOKEN"
    const val STARTING_COUNT_NOT_ALLOWED = "STARTING_COUNT_NOT_ALLOWED"
    const val MARATHON_NOT_STARTED = "MARATHON_NOT_STARTED"
    const val MARATHON_NOT_RUNNING = "MARATHON_NOT_RUNNING"
    const val MARATHON_ALREADY_ACTIVE = "MARATHON_ALREADY_ACTIVE"
    const val UNDO_EXPIRED = "UNDO_EXPIRED"
    const val RESTORE_EXPIRED = "RESTORE_EXPIRED"
    const val NETWORK = "NETWORK"
}

@Singleton
class ChalisaRepository @Inject constructor(
    private val apiService: ApiService,
    private val chalisaStore: ChalisaStore,
    moshi: Moshi
) {
    private val errorAdapter = moshi.adapter(ChalisaErrorEnvelope::class.java)

    private suspend fun <T> request(
        fallback: String,
        call: suspend () -> Response<ApiResponse<T>>
    ): Result<T> = try {
        val response = call()
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            Result.success(body.data)
        } else {
            val error = response.errorBody()?.string()
                ?.takeIf(String::isNotBlank)
                ?.let { runCatching { errorAdapter.fromJson(it)?.error }.getOrNull() }
            if (error?.code == ChalisaErrorCodes.INVALID_GUEST_TOKEN) chalisaStore.clearGuestToken()
            val retryAfter = ((error?.details as? Map<*, *>)?.get("retryAfterSeconds") as? Number)?.toInt()
            val message = error?.message?.takeIf { it.isNotBlank() && it != "Internal server error" }
                ?: "$fallback. Please try again."
            Result.failure(ChalisaException(error?.code, message, retryAfter))
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.failure(ChalisaException(ChalisaErrorCodes.NETWORK, networkErrorMessage(error)))
    } catch (error: Exception) {
        Result.failure(ChalisaException(null, "$fallback. Please try again."))
    }

    suspend fun summary() = request("Couldn't load the Hanuman Chalisa marathon") {
        apiService.getChalisaSummary()
    }

    suspend fun board() = request("Couldn't load the marathon board") {
        apiService.getChalisaBoard()
    }

    suspend fun myStats(month: String? = null) = request("Couldn't load your readings") {
        apiService.getMyChalisa(month)
    }

    suspend fun archive() = request("Couldn't load past marathons") {
        apiService.getChalisaArchive()
    }

    /** Saves the name on the board. A new guest's pass is stored on this phone. */
    suspend fun join(displayName: String): Result<ChalisaParticipant> =
        request("Couldn't save your name") {
            apiService.saveChalisaParticipant(ChalisaParticipantRequest(displayName.trim()))
        }.onSuccess { result ->
            result.guestToken?.let { chalisaStore.saveGuestToken(it) }
        }.map { it.participant }

    /** Counts one round. A new id per tap means a retried request is never counted twice. */
    suspend fun addRound(clientRequestId: String = UUID.randomUUID().toString()) =
        request("Couldn't count this round") {
            apiService.addChalisaReading(ChalisaReadingRequest(clientRequestId))
        }

    suspend fun addStartingCount(count: Int, clientRequestId: String = UUID.randomUUID().toString()) =
        request("Couldn't add your starting count") {
            apiService.addChalisaReading(ChalisaReadingRequest(clientRequestId, startingCount = count))
        }

    suspend fun undo(clientRequestId: String) = request("Couldn't undo this round") {
        apiService.undoChalisaReading(clientRequestId)
    }

    // ---- Super admin ----

    suspend fun schedule(title: String, startsAt: Instant?, goal: Long?) =
        request("Couldn't schedule the marathon") {
            apiService.scheduleChalisaMarathon(
                ChalisaScheduleRequest(title.trim(), startsAt?.toString(), goal)
            )
        }.map { it.marathon }

    suspend fun update(marathonId: String, title: String?, startsAt: Instant?, goal: Long?) =
        request("Couldn't save the changes") {
            apiService.updateChalisaMarathon(
                marathonId,
                ChalisaUpdateRequest(title?.trim(), startsAt?.toString(), goal)
            )
        }.map { it.marathon }

    suspend fun cancel(marathonId: String) = request("Couldn't cancel the marathon") {
        apiService.cancelChalisaMarathon(marathonId)
    }

    suspend fun end(marathonId: String, confirmText: String) = request("Couldn't end the marathon") {
        apiService.endChalisaMarathon(marathonId, ChalisaEndRequest(confirmText.trim()))
    }.map { it.archived }

    suspend fun restore(marathonId: String) = request("Couldn't undo ending the marathon") {
        apiService.restoreChalisaMarathon(marathonId)
    }.map { it.marathon }
}
