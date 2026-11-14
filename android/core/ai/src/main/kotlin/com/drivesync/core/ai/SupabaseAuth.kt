package com.drivesync.core.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAtEpochSeconds: Long,
)

/** Where the session lives. Android: Keystore-encrypted storage (기획서 3-⑥). */
interface TokenStorage {
    fun load(): AuthSession?
    fun save(session: AuthSession)
    fun clear()
}

sealed interface AuthResult {
    data object SignedIn : AuthResult
    data class Failed(val message: String) : AuthResult
}

/**
 * Minimal Supabase GoTrue client: password sign-in + refresh. Provides the access token
 * that [EdgeFunctionClient] sends with every call.
 */
class SupabaseAuth(
    supabaseUrl: String,
    private val anonKey: String,
    private val storage: TokenStorage,
    private val http: OkHttpClient,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {
    private val tokenUrl = supabaseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments("auth/v1/token").build()
    private val json = EdgeFunctionClient.DefaultJson
    private val refreshLock = Mutex()

    suspend fun signIn(email: String, password: String): AuthResult {
        val body = """{"email":${json.encodeToString(String.serializer(), email)},"password":${json.encodeToString(String.serializer(), password)}}"""
        return request("password", body)?.let {
            storage.save(it)
            AuthResult.SignedIn
        } ?: AuthResult.Failed("로그인에 실패했습니다")
    }

    /** Returns a valid access token, refreshing it when it expires within a minute. */
    suspend fun accessToken(): String? = refreshLock.withLock {
        val session = storage.load() ?: return@withLock null
        if (session.expiresAtEpochSeconds - nowEpochSeconds() > REFRESH_MARGIN_SECONDS) return@withLock session.accessToken
        val refreshed = request("refresh_token", """{"refresh_token":"${session.refreshToken}"}""")
        if (refreshed == null) {
            null
        } else {
            storage.save(refreshed)
            refreshed.accessToken
        }
    }

    fun signOut() = storage.clear()

    private suspend fun request(grantType: String, body: String): AuthSession? = withContext(Dispatchers.IO) {
        blockingRequest(grantType, body)
    }

    private fun blockingRequest(grantType: String, body: String): AuthSession? {
        val request = Request.Builder()
            .url(tokenUrl.newBuilder().addQueryParameter("grant_type", grantType).build())
            .header("apikey", anonKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                json.decodeFromString(AuthSession.serializer(), response.body?.string().orEmpty())
            }
        } catch (e: IOException) {
            null
        }
    }

    private companion object {
        const val REFRESH_MARGIN_SECONDS = 60
    }
}
