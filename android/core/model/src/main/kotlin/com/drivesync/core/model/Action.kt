package com.drivesync.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    @SerialName("email") EMAIL,
}

/** The 6-state human-in-the-loop lifecycle (기획서 2-③). */
@Serializable
enum class ActionStatus {
    @SerialName("draft") DRAFT,
    @SerialName("review") REVIEW,
    @SerialName("approved") APPROVED,
    @SerialName("executed") EXECUTED,
    @SerialName("failed") FAILED,
    @SerialName("retry") RETRY,
}

@Serializable
data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String,
)

@Serializable
data class ActionLogEntry(
    val atMillis: Long,
    val from: ActionStatus?,
    val to: ActionStatus,
    val message: String,
)

@Serializable
data class FollowUpAction(
    val id: String,
    val sessionId: String,
    val customerId: String,
    val type: ActionType,
    val draft: EmailDraft,
    val status: ActionStatus,
    /** Incremented on every edit; part of the idempotency key so an edited draft is a new execution. */
    val draftVersion: Int = 1,
    /** Assigned at approval. Same key on retry => the server never sends twice. */
    val idempotencyKey: String? = null,
    val failureReason: String? = null,
    val log: List<ActionLogEntry> = emptyList(),
)
