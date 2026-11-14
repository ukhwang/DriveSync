package com.drivesync.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Upsert
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observe(id: String): Flow<CustomerEntity?>

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface VisitDao {
    @Upsert
    suspend fun upsertAll(visits: List<VisitEntity>)

    @Query("SELECT * FROM visits ORDER BY scheduledAtEpochMillis")
    fun observeAll(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE id = :id")
    suspend fun get(id: String): VisitEntity?

    @Query("SELECT COUNT(*) FROM visits")
    suspend fun count(): Int

    @Query("DELETE FROM visits WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM visits WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: String)
}

@Dao
interface SessionSnapshotDao {
    @Upsert
    suspend fun save(snapshot: SessionSnapshotEntity)

    @Query("SELECT * FROM session_snapshot WHERE slot = 0")
    suspend fun load(): SessionSnapshotEntity?

    @Query("DELETE FROM session_snapshot")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE sessionId = :sessionId ORDER BY createdAtMillis")
    fun observeSession(sessionId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE synced = 0 ORDER BY createdAtMillis")
    suspend fun unsynced(): List<NoteEntity>

    @Query("UPDATE notes SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM notes WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface InsightDao {
    @Upsert
    suspend fun upsert(insight: InsightEntity)

    @Query("SELECT * FROM insights WHERE visitId = :visitId")
    fun observe(visitId: String): Flow<InsightEntity?>

    @Query("DELETE FROM insights WHERE visitId = :visitId")
    suspend fun delete(visitId: String)
}

@Dao
interface ActionDao {
    @Upsert
    suspend fun upsert(action: ActionEntity)

    @Query("SELECT * FROM actions WHERE id = :id")
    suspend fun get(id: String): ActionEntity?

    @Query("SELECT * FROM actions WHERE sessionId = :sessionId ORDER BY id")
    fun observeSession(sessionId: String): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE customerId = :customerId AND status != 'executed'")
    suspend fun openForCustomer(customerId: String): List<ActionEntity>

    @Query("DELETE FROM actions WHERE customerId = :customerId")
    suspend fun deleteForCustomer(customerId: String)
}
