package com.drivesync.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        CustomerEntity::class,
        VisitEntity::class,
        SessionSnapshotEntity::class,
        NoteEntity::class,
        InsightEntity::class,
        ActionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DriveSyncDatabase : RoomDatabase() {
    abstract fun customers(): CustomerDao
    abstract fun visits(): VisitDao
    abstract fun sessionSnapshots(): SessionSnapshotDao
    abstract fun notes(): NoteDao
    abstract fun insights(): InsightDao
    abstract fun actions(): ActionDao

    companion object {
        private const val FILE_NAME = "drivesync.db"

        /**
         * Opens the SQLCipher-encrypted database. The passphrase is random per install and
         * wrapped by an Android Keystore key (see [KeystorePassphraseProvider]).
         */
        fun open(context: Context, passphrase: ByteArray): DriveSyncDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context.applicationContext, DriveSyncDatabase::class.java, FILE_NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
