package com.drivesync.app.di

import android.content.Context
import com.drivesync.app.BuildConfig
import com.drivesync.app.vehicle.CarApiVehicleContextSource
import com.drivesync.core.action.ActionEngine
import com.drivesync.core.ai.AiGateway
import com.drivesync.core.ai.AuthSession
import com.drivesync.core.ai.EdgeFunctionAiGateway
import com.drivesync.core.ai.EdgeFunctionClient
import com.drivesync.core.ai.HttpActionExecutor
import com.drivesync.core.ai.SupabaseAuth
import com.drivesync.core.ai.TokenStorage
import com.drivesync.core.data.DriveSyncDatabase
import com.drivesync.core.data.InsightRepository
import com.drivesync.core.data.KeystorePassphraseProvider
import com.drivesync.core.data.KeystoreSecretStore
import com.drivesync.core.data.NoteRepository
import com.drivesync.core.data.RoomActionStore
import com.drivesync.core.data.SessionSnapshotRepository
import com.drivesync.core.data.VisitRepository
import com.drivesync.core.safety.SafetyPolicy
import com.drivesync.core.vehicle.MockVehicleContextSource
import com.drivesync.core.vehicle.VehicleContextSource
import com.drivesync.core.voice.AndroidSpeechInput
import com.drivesync.core.voice.AndroidSpeechOutput
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container (kept small on purpose; swap for Hilt when modules grow).
 * Every port has exactly one production binding here, so tests replace them at the port.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val secrets = KeystoreSecretStore(appContext)

    val database: DriveSyncDatabase =
        DriveSyncDatabase.open(appContext, KeystorePassphraseProvider(secrets).databasePassphrase())

    /** Present only in simulator mode; drives the operator panel and the "시뮬레이션" badge. */
    val simulator: MockVehicleContextSource? = if (BuildConfig.USE_CAR_API) null else MockVehicleContextSource()

    val vehicleSource: VehicleContextSource = simulator ?: CarApiVehicleContextSource(appContext)

    val safetyPolicy = SafetyPolicy()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val auth = SupabaseAuth(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifEmpty { "http://localhost:54321" },
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        storage = KeystoreTokenStorage(secrets),
        http = http,
    )

    private val edgeClient = EdgeFunctionClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifEmpty { "http://localhost:54321" },
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        accessToken = auth::accessToken,
        http = http,
    )

    val ai: AiGateway = EdgeFunctionAiGateway(edgeClient)
    val actionStore = RoomActionStore(database.actions())
    val actionEngine = ActionEngine(actionStore, HttpActionExecutor(edgeClient))

    val visits = VisitRepository(database)
    val notes = NoteRepository(database.notes())
    val insights = InsightRepository(database.insights())
    val sessionSnapshots = SessionSnapshotRepository(database.sessionSnapshots())

    val speechOutput = AndroidSpeechOutput(appContext)
    val speechInput = AndroidSpeechInput(appContext)
}

private class KeystoreTokenStorage(private val secrets: KeystoreSecretStore) : TokenStorage {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): AuthSession? = runCatching {
        secrets.read(KEY)?.let { json.decodeFromString(AuthSession.serializer(), it.decodeToString()) }
    }.getOrNull()

    override fun save(session: AuthSession) =
        secrets.write(KEY, json.encodeToString(AuthSession.serializer(), session).encodeToByteArray())

    override fun clear() = secrets.remove(KEY)

    private companion object {
        const val KEY = "auth.session"
    }
}
