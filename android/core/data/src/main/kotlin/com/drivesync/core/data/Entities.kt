package com.drivesync.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Local Store (기획서 3-③-6). Everything in this database is encrypted at rest by SQLCipher.
 * Structured payloads (drafts, insights, logs) are stored as JSON produced by the shared
 * kotlinx.serialization models, so the wire contract and the disk format never drift apart.
 */

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val companyName: String,
    val contactName: String,
    val contactEmail: String,
    val historySummary: String,
)

@Entity(tableName = "visits", indices = [Index("customerId")])
data class VisitEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val purpose: String,
    val scheduledAtEpochMillis: Long,
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
)

/** Single-row snapshot of the running session, used for crash/background recovery. */
@Entity(tableName = "session_snapshot")
data class SessionSnapshotEntity(
    @PrimaryKey val slot: Int = 0,
    val sessionId: String,
    val phase: String,
    val visitId: String?,
    val resumePhase: String?,
    val approvalsOnHold: Boolean,
    val etaMinutes: Int?,
    val updatedAtMillis: Long,
)

@Entity(tableName = "notes", indices = [Index("sessionId")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val text: String,
    val createdAtMillis: Long,
    val inputMethod: String,
    val phase: String,
    /** False until the note has been sent to the server; network loss keeps it queued. */
    val synced: Boolean,
)

@Entity(tableName = "insights")
data class InsightEntity(
    @PrimaryKey val visitId: String,
    val json: String,
    val createdAtMillis: Long,
)

@Entity(tableName = "actions", indices = [Index("sessionId"), Index("customerId")])
data class ActionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val customerId: String,
    val status: String,
    val json: String,
)
