package com.drivesync.core.action

import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.FollowUpAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Persistence port for follow-up actions. Production: Room + SQLCipher (core:data).
 * This is server/persisted state; the UI observes it and never copies it into UI state.
 */
interface ActionStore {
    suspend fun get(id: String): FollowUpAction?
    suspend fun upsert(action: FollowUpAction)
    fun observeSession(sessionId: String): Flow<List<FollowUpAction>>

    /** Unfinished actions for a customer; carried into the next visit briefing. */
    suspend fun openForCustomer(customerId: String): List<FollowUpAction>
}

val FollowUpAction.isOpen: Boolean get() = status != ActionStatus.EXECUTED

class InMemoryActionStore : ActionStore {
    private val items = MutableStateFlow<Map<String, FollowUpAction>>(emptyMap())

    override suspend fun get(id: String): FollowUpAction? = items.value[id]

    override suspend fun upsert(action: FollowUpAction) = items.update { it + (action.id to action) }

    override fun observeSession(sessionId: String): Flow<List<FollowUpAction>> =
        items.map { all -> all.values.filter { it.sessionId == sessionId }.sortedBy { it.id } }

    override suspend fun openForCustomer(customerId: String): List<FollowUpAction> =
        items.value.values.filter { it.customerId == customerId && it.isOpen }
}
