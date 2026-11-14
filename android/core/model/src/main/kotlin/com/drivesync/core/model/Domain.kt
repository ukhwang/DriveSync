package com.drivesync.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class Customer(
    val id: String,
    val companyName: String,
    val contactName: String,
    val contactEmail: String,
    /** Rolling summary of every previous visit; injected into the next briefing. */
    val historySummary: String,
)

@Serializable
data class Visit(
    val id: String,
    val customerId: String,
    val purpose: String,
    val scheduledAtEpochMillis: Long,
    val placeName: String,
    val place: GeoPoint,
)

/** Session state machine phases (기획서 3-④). */
@Serializable
enum class SessionPhase {
    @SerialName("idle") IDLE,
    @SerialName("prepare_session") PREPARE_SESSION,
    @SerialName("driving_limited") DRIVING_LIMITED,
    @SerialName("paused_by_policy") PAUSED_BY_POLICY,
    @SerialName("arrival_pending") ARRIVAL_PENDING,
    @SerialName("parked_review") PARKED_REVIEW,
    @SerialName("awaiting_approval") AWAITING_APPROVAL,
    @SerialName("executing") EXECUTING,
    @SerialName("completed") COMPLETED,
    @SerialName("error") ERROR,
}

@Serializable
enum class NoteInputMethod {
    @SerialName("voice") VOICE,
    @SerialName("text") TEXT,
}

@Serializable
data class Note(
    val id: String,
    val sessionId: String,
    val text: String,
    val createdAtMillis: Long,
    val inputMethod: NoteInputMethod,
    val phase: SessionPhase,
)

/** Whether an Insight line is something the user said or something the AI inferred. */
@Serializable
enum class ItemSource {
    @SerialName("user") USER,
    @SerialName("ai") AI,
}

@Serializable
data class SourcedText(
    val text: String,
    val source: ItemSource,
)

/** Meeting cheat sheet. Exactly 3 summary lines; validated on server AND client. */
@Serializable
data class Insight(
    val summary: List<SourcedText>,
    val concerns: List<SourcedText>,
    val expectedQuestions: List<SourcedText>,
)

@Serializable
enum class BriefingTier {
    /** ETA < 10 min */
    @SerialName("short") SHORT,

    /** 10 <= ETA < 25 min */
    @SerialName("medium") MEDIUM,

    /** ETA >= 25 min */
    @SerialName("long") LONG,
}

@Serializable
data class BriefingSentence(
    val text: String,
    /** Core sentences survive when the remaining ETA is too short for the full script. */
    val core: Boolean,
)

@Serializable
data class Briefing(
    val tier: BriefingTier,
    val screenLines: List<String>,
    val voiceScript: List<BriefingSentence>,
    /** 15-second recap played 5 minutes before arrival. Null for the SHORT tier. */
    val preArrivalRecap: String? = null,
)
