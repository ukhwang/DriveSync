package com.drivesync.core.data

import com.drivesync.core.action.ActionStore
import com.drivesync.core.model.Customer
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.model.GeoPoint
import com.drivesync.core.model.Insight
import com.drivesync.core.model.Note
import com.drivesync.core.model.NoteInputMethod
import com.drivesync.core.model.SessionPhase
import com.drivesync.core.model.Visit
import com.drivesync.core.session.SessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID

private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Room-backed [ActionStore]. Encrypted at rest; observed directly by the UI. */
class RoomActionStore(private val dao: ActionDao) : ActionStore {
    override suspend fun get(id: String): FollowUpAction? = dao.get(id)?.toModel()

    override suspend fun upsert(action: FollowUpAction) = dao.upsert(
        ActionEntity(
            id = action.id,
            sessionId = action.sessionId,
            customerId = action.customerId,
            status = action.status.name.lowercase(),
            json = codec.encodeToString(FollowUpAction.serializer(), action),
        ),
    )

    override fun observeSession(sessionId: String): Flow<List<FollowUpAction>> =
        dao.observeSession(sessionId).map { rows -> rows.map { it.toModel() } }

    override suspend fun openForCustomer(customerId: String): List<FollowUpAction> =
        dao.openForCustomer(customerId).map { it.toModel() }

    private fun ActionEntity.toModel(): FollowUpAction = codec.decodeFromString(FollowUpAction.serializer(), json)
}

class VisitRepository(private val db: DriveSyncDatabase) {

    fun observeVisits(): Flow<List<Visit>> = db.visits().observeAll().map { rows ->
        rows.map {
            Visit(it.id, it.customerId, it.purpose, it.scheduledAtEpochMillis, it.placeName, GeoPoint(it.latitude, it.longitude))
        }
    }

    suspend fun visit(id: String): Visit? = db.visits().get(id)?.let {
        Visit(it.id, it.customerId, it.purpose, it.scheduledAtEpochMillis, it.placeName, GeoPoint(it.latitude, it.longitude))
    }

    fun observeCustomer(id: String): Flow<Customer?> = db.customers().observe(id).map { row ->
        row?.let { Customer(it.id, it.companyName, it.contactName, it.contactEmail, it.historySummary) }
    }

    suspend fun isEmpty(): Boolean = db.visits().count() == 0

    suspend fun seed(customers: List<Customer>, visits: List<Visit>) {
        db.customers().upsertAll(
            customers.map { CustomerEntity(it.id, it.companyName, it.contactName, it.contactEmail, it.historySummary) },
        )
        db.visits().upsertAll(
            visits.map {
                VisitEntity(it.id, it.customerId, it.purpose, it.scheduledAtEpochMillis, it.placeName, it.place.latitude, it.place.longitude)
            },
        )
    }

    /** 기획서 3-⑥: 사용자는 고객 단위로 기록을 지울 수 있습니다. */
    suspend fun deleteCustomerData(customerId: String) {
        db.actions().deleteForCustomer(customerId)
        db.visits().deleteForCustomer(customerId)
        db.customers().delete(customerId)
    }
}

class NoteRepository(private val dao: NoteDao, private val clock: () -> Long = System::currentTimeMillis) {

    /** Always written locally first; never lost on network failure. */
    suspend fun add(sessionId: String, text: String, method: NoteInputMethod, phase: SessionPhase): Note {
        val note = Note(UUID.randomUUID().toString(), sessionId, text, clock(), method, phase)
        dao.insert(NoteEntity(note.id, sessionId, text, note.createdAtMillis, method.name, phase.name, synced = false))
        return note
    }

    fun observeSession(sessionId: String): Flow<List<Note>> = dao.observeSession(sessionId).map { rows ->
        rows.map {
            Note(it.id, it.sessionId, it.text, it.createdAtMillis, NoteInputMethod.valueOf(it.inputMethod), SessionPhase.valueOf(it.phase))
        }
    }

    suspend fun markSynced(ids: List<String>) = dao.markSynced(ids)

    suspend fun deleteSession(sessionId: String) = dao.deleteSession(sessionId)
}

class InsightRepository(private val dao: InsightDao, private val clock: () -> Long = System::currentTimeMillis) {
    suspend fun save(visitId: String, insight: Insight) =
        dao.upsert(InsightEntity(visitId, codec.encodeToString(Insight.serializer(), insight), clock()))

    fun observe(visitId: String): Flow<Insight?> =
        dao.observe(visitId).map { row -> row?.let { codec.decodeFromString(Insight.serializer(), it.json) } }
}

class SessionSnapshotRepository(private val dao: SessionSnapshotDao, private val clock: () -> Long = System::currentTimeMillis) {

    suspend fun save(sessionId: String, state: SessionState) = dao.save(
        SessionSnapshotEntity(
            sessionId = sessionId,
            phase = state.phase.name,
            visitId = state.visitId,
            resumePhase = state.resumePhase?.name,
            approvalsOnHold = state.approvalsOnHold,
            etaMinutes = state.etaMinutes,
            updatedAtMillis = clock(),
        ),
    )

    suspend fun load(): Pair<String, SessionState>? = dao.load()?.let {
        it.sessionId to SessionState(
            phase = SessionPhase.valueOf(it.phase),
            visitId = it.visitId,
            resumePhase = it.resumePhase?.let(SessionPhase::valueOf),
            approvalsOnHold = it.approvalsOnHold,
            etaMinutes = it.etaMinutes,
        )
    }

    suspend fun clear() = dao.clear()
}
