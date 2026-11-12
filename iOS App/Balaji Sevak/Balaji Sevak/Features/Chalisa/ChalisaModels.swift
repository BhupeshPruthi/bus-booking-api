import Foundation

// Hanuman Chalisa counter. Mirrors Android `ChalisaDto.kt`; dates are ISO-8601 strings
// parsed with `AppFormatters.parseISO`.

nonisolated struct ChalisaMarathon: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let goal: Int?
    let startsAt: String
    let endedAt: String?
    /// "scheduled" | "running" | "ended"
    let state: String
}

nonisolated struct ChalisaArchivedMarathon: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let goal: Int?
    let startsAt: String
    let endedAt: String?
    let state: String
    let finalTotal: Int
    let finalParticipantCount: Int
    let endedBy: String?
    /// Only sent right after ending: the super admin can undo until then.
    let restoreUntil: String?
}

nonisolated struct ChalisaSummary: Codable, Equatable {
    /// "none" | "scheduled" | "running"
    let state: String
    let marathon: ChalisaMarathon?
    let total: Int
    let participantCount: Int
    let myToday: Int
    let serverTime: String?
    let lastEnded: ChalisaArchivedMarathon?
}

nonisolated struct ChalisaParticipant: Codable, Equatable, Identifiable {
    let id: String
    let displayName: String
    let isGuest: Bool
}

nonisolated struct ChalisaBoardEntry: Codable, Equatable, Identifiable {
    let rank: Int
    let participantId: String
    let displayName: String
    let isGuest: Bool
    /// True when a Chalisa admin added rounds to this entry in one go.
    let hasAdminAdditions: Bool
    let count: Int
    let lastReadAt: String?
    var id: String { participantId }

    private enum CodingKeys: String, CodingKey { case rank, participantId, displayName, isGuest, hasAdminAdditions, count, lastReadAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rank = try c.decode(Int.self, forKey: .rank)
        participantId = try c.decode(String.self, forKey: .participantId)
        displayName = try c.decode(String.self, forKey: .displayName)
        isGuest = try c.decode(Bool.self, forKey: .isGuest)
        hasAdminAdditions = try c.decodeIfPresent(Bool.self, forKey: .hasAdminAdditions) ?? false
        count = try c.decode(Int.self, forKey: .count)
        lastReadAt = try c.decodeIfPresent(String.self, forKey: .lastReadAt)
    }
}

nonisolated struct ChalisaMyRank: Codable, Equatable {
    let participantId: String
    let rank: Int?
    var count: Int
}

nonisolated struct ChalisaBoard: Codable, Equatable {
    let marathon: ChalisaMarathon?
    var total: Int
    var participantCount: Int
    let leaderboard: [ChalisaBoardEntry]
    var me: ChalisaMyRank?
    let serverTime: String?
    let refreshAfterSeconds: Int
}

nonisolated struct ChalisaDay: Codable, Equatable, Identifiable {
    let date: String
    let count: Int
    var id: String { date }
}

nonisolated struct ChalisaMyMarathon: Codable, Equatable, Identifiable {
    let id: String
    let title: String
    let count: Int
    let rank: Int
    let state: String
    let startsAt: String
    let endedAt: String?
}

nonisolated struct ChalisaMyStats: Codable, Equatable {
    let participant: ChalisaParticipant?
    var lifetime: Int
    var today: Int
    let thisMonth: Int
    let streakDays: Int
    let firstReadDate: String?
    let month: String
    let days: [ChalisaDay]
    let marathons: [ChalisaMyMarathon]
    var lastReadAt: String?
    var canSetStartingCount: Bool
    let maxStartingCount: Int
    let minRoundSeconds: Int
}

nonisolated struct ChalisaMarathonCounts: Codable, Equatable {
    let id: String
    let title: String
    let state: String
    let myCount: Int
    let total: Int
    let participantCount: Int
}

nonisolated struct ChalisaReading: Codable, Equatable {
    let clientRequestId: String
    /// "round" | "opening" | "admin" (rounds a Chalisa admin added in one go)
    let kind: String
    let count: Int
    let readAt: String
    let undoUntil: String
}

/// Returned by both counting and undo.
nonisolated struct ChalisaCounts: Codable, Equatable {
    let reading: ChalisaReading?
    let participant: ChalisaParticipant?
    let today: Int
    let lifetime: Int
    let marathon: ChalisaMarathonCounts?
    let lastReadAt: String?
    let canSetStartingCount: Bool
    /// The request id was already used: an earlier tap whose answer was lost was counted then.
    let duplicate: Bool

    private enum CodingKeys: String, CodingKey { case reading, participant, today, lifetime, marathon, lastReadAt, canSetStartingCount, duplicate }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        reading = try c.decodeIfPresent(ChalisaReading.self, forKey: .reading)
        participant = try c.decodeIfPresent(ChalisaParticipant.self, forKey: .participant)
        today = try c.decodeIfPresent(Int.self, forKey: .today) ?? 0
        lifetime = try c.decodeIfPresent(Int.self, forKey: .lifetime) ?? 0
        marathon = try c.decodeIfPresent(ChalisaMarathonCounts.self, forKey: .marathon)
        lastReadAt = try c.decodeIfPresent(String.self, forKey: .lastReadAt)
        canSetStartingCount = try c.decodeIfPresent(Bool.self, forKey: .canSetStartingCount) ?? false
        duplicate = try c.decodeIfPresent(Bool.self, forKey: .duplicate) ?? false
    }
}

// MARK: - Requests and results

nonisolated struct ChalisaParticipantRequest: Encodable { let displayName: String }

nonisolated struct ChalisaReadingRequest: Encodable {
    let clientRequestId: String
    var startingCount: Int? = nil
}

/// Rounds a Chalisa admin adds to their own entry in one go.
nonisolated struct ChalisaAdminReadingRequest: Encodable {
    let clientRequestId: String
    let count: Int
}

nonisolated struct ChalisaScheduleRequest: Encodable {
    let title: String
    /// ISO-8601 instant; omitted to start now.
    let startsAt: String?
    let goal: Int?
}

/// Only the fields that are set are sent, so leaving one out keeps its current value.
nonisolated struct ChalisaUpdateRequest: Encodable {
    let title: String?
    let startsAt: String?
    let goal: Int?
}

nonisolated struct ChalisaEndRequest: Encodable { let confirmText: String }

nonisolated struct ChalisaParticipantResult: Decodable {
    let participant: ChalisaParticipant
    let guestToken: String?
}

nonisolated struct ChalisaMarathonResult: Decodable { let marathon: ChalisaMarathon }
nonisolated struct ChalisaEndResult: Decodable { let archived: ChalisaArchivedMarathon }
nonisolated struct ChalisaCancelResult: Decodable { let cancelled: Bool }
