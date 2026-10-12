package com.mybus.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---- Hanuman Chalisa counter ----

@JsonClass(generateAdapter = true)
data class ChalisaMarathon(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "goal") val goal: Long? = null,
    @Json(name = "startsAt") val startsAt: String,
    @Json(name = "endedAt") val endedAt: String? = null,
    /** "scheduled" | "running" | "ended" */
    @Json(name = "state") val state: String
)

@JsonClass(generateAdapter = true)
data class ChalisaArchivedMarathon(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "goal") val goal: Long? = null,
    @Json(name = "startsAt") val startsAt: String,
    @Json(name = "endedAt") val endedAt: String? = null,
    @Json(name = "state") val state: String,
    @Json(name = "finalTotal") val finalTotal: Long = 0,
    @Json(name = "finalParticipantCount") val finalParticipantCount: Int = 0,
    @Json(name = "endedBy") val endedBy: String? = null,
    @Json(name = "restoreUntil") val restoreUntil: String? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaSummary(
    /** "none" | "scheduled" | "running" */
    @Json(name = "state") val state: String,
    @Json(name = "marathon") val marathon: ChalisaMarathon? = null,
    @Json(name = "total") val total: Long = 0,
    @Json(name = "participantCount") val participantCount: Int = 0,
    @Json(name = "myToday") val myToday: Int = 0,
    @Json(name = "serverTime") val serverTime: String? = null,
    @Json(name = "lastEnded") val lastEnded: ChalisaArchivedMarathon? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaParticipant(
    @Json(name = "id") val id: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "isGuest") val isGuest: Boolean
)

@JsonClass(generateAdapter = true)
data class ChalisaBoardEntry(
    @Json(name = "rank") val rank: Int,
    @Json(name = "participantId") val participantId: String,
    @Json(name = "displayName") val displayName: String,
    @Json(name = "isGuest") val isGuest: Boolean,
    /** True when a Chalisa admin added rounds to this entry in one go. */
    @Json(name = "hasAdminAdditions") val hasAdminAdditions: Boolean = false,
    @Json(name = "count") val count: Long,
    @Json(name = "lastReadAt") val lastReadAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaMyRank(
    @Json(name = "participantId") val participantId: String,
    @Json(name = "rank") val rank: Int? = null,
    @Json(name = "count") val count: Long = 0
)

@JsonClass(generateAdapter = true)
data class ChalisaBoard(
    @Json(name = "marathon") val marathon: ChalisaMarathon? = null,
    @Json(name = "total") val total: Long = 0,
    @Json(name = "participantCount") val participantCount: Int = 0,
    @Json(name = "leaderboard") val leaderboard: List<ChalisaBoardEntry> = emptyList(),
    @Json(name = "me") val me: ChalisaMyRank? = null,
    @Json(name = "serverTime") val serverTime: String? = null,
    @Json(name = "refreshAfterSeconds") val refreshAfterSeconds: Int = 30
)

@JsonClass(generateAdapter = true)
data class ChalisaDay(
    @Json(name = "date") val date: String,
    @Json(name = "count") val count: Int
)

@JsonClass(generateAdapter = true)
data class ChalisaMyMarathon(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "count") val count: Long,
    @Json(name = "rank") val rank: Int,
    @Json(name = "state") val state: String,
    @Json(name = "startsAt") val startsAt: String,
    @Json(name = "endedAt") val endedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaMyStats(
    @Json(name = "participant") val participant: ChalisaParticipant? = null,
    @Json(name = "lifetime") val lifetime: Long = 0,
    @Json(name = "today") val today: Int = 0,
    @Json(name = "thisMonth") val thisMonth: Int = 0,
    @Json(name = "streakDays") val streakDays: Int = 0,
    @Json(name = "firstReadDate") val firstReadDate: String? = null,
    @Json(name = "month") val month: String,
    @Json(name = "days") val days: List<ChalisaDay> = emptyList(),
    @Json(name = "marathons") val marathons: List<ChalisaMyMarathon> = emptyList(),
    @Json(name = "lastReadAt") val lastReadAt: String? = null,
    @Json(name = "canSetStartingCount") val canSetStartingCount: Boolean = false,
    @Json(name = "maxStartingCount") val maxStartingCount: Int = 5000,
    @Json(name = "minRoundSeconds") val minRoundSeconds: Int = 30
)

@JsonClass(generateAdapter = true)
data class ChalisaMarathonCounts(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String,
    @Json(name = "state") val state: String,
    @Json(name = "myCount") val myCount: Long = 0,
    @Json(name = "total") val total: Long = 0,
    @Json(name = "participantCount") val participantCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class ChalisaReading(
    @Json(name = "clientRequestId") val clientRequestId: String,
    /** "round" | "opening" | "admin" (rounds a Chalisa admin added in one go) */
    @Json(name = "kind") val kind: String,
    @Json(name = "count") val count: Int,
    @Json(name = "readAt") val readAt: String,
    @Json(name = "undoUntil") val undoUntil: String
)

/** Returned by both counting and undo. */
@JsonClass(generateAdapter = true)
data class ChalisaCounts(
    @Json(name = "reading") val reading: ChalisaReading? = null,
    @Json(name = "participant") val participant: ChalisaParticipant? = null,
    @Json(name = "today") val today: Int = 0,
    @Json(name = "lifetime") val lifetime: Long = 0,
    @Json(name = "marathon") val marathon: ChalisaMarathonCounts? = null,
    @Json(name = "lastReadAt") val lastReadAt: String? = null,
    @Json(name = "canSetStartingCount") val canSetStartingCount: Boolean = false,
    @Json(name = "duplicate") val duplicate: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ChalisaParticipantRequest(
    @Json(name = "displayName") val displayName: String
)

@JsonClass(generateAdapter = true)
data class ChalisaParticipantResult(
    @Json(name = "participant") val participant: ChalisaParticipant,
    @Json(name = "guestToken") val guestToken: String? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaReadingRequest(
    @Json(name = "clientRequestId") val clientRequestId: String,
    @Json(name = "startingCount") val startingCount: Int? = null
)

/** Rounds a Chalisa admin adds to their own entry in one go. */
@JsonClass(generateAdapter = true)
data class ChalisaAdminReadingRequest(
    @Json(name = "clientRequestId") val clientRequestId: String,
    @Json(name = "count") val count: Int
)

@JsonClass(generateAdapter = true)
data class ChalisaScheduleRequest(
    @Json(name = "title") val title: String,
    /** ISO-8601 instant; omitted to start now. */
    @Json(name = "startsAt") val startsAt: String? = null,
    @Json(name = "goal") val goal: Long? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaUpdateRequest(
    @Json(name = "title") val title: String? = null,
    @Json(name = "startsAt") val startsAt: String? = null,
    @Json(name = "goal") val goal: Long? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaEndRequest(
    @Json(name = "confirmText") val confirmText: String
)

@JsonClass(generateAdapter = true)
data class ChalisaMarathonResult(
    @Json(name = "marathon") val marathon: ChalisaMarathon
)

@JsonClass(generateAdapter = true)
data class ChalisaEndResult(
    @Json(name = "archived") val archived: ChalisaArchivedMarathon
)

@JsonClass(generateAdapter = true)
data class ChalisaCancelResult(
    @Json(name = "cancelled") val cancelled: Boolean
)

/** Chalisa errors carry object details (e.g. retryAfterSeconds), unlike the shared ApiError list. */
@JsonClass(generateAdapter = true)
data class ChalisaErrorEnvelope(
    @Json(name = "error") val error: ChalisaError? = null
)

@JsonClass(generateAdapter = true)
data class ChalisaError(
    @Json(name = "code") val code: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "details") val details: Any? = null
)
