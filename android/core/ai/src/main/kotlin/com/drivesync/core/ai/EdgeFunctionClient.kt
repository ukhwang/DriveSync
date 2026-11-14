package com.drivesync.core.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Every failure mode the UI has to handle explicitly. No exceptions leak past the gateway. */
sealed interface EdgeResult<out T> {
    data class Success<T>(val value: T) : EdgeResult<T>

    /** Server or client rejected the payload against the schema. Raw notes stay saved locally. */
    data class SchemaRejected(val detail: String) : EdgeResult<Nothing>

    /** User has not agreed to send customer data to the external LLM (기획서 3-⑥). */
    data object ConsentRequired : EdgeResult<Nothing>

    data object Unauthorized : EdgeResult<Nothing>

    data class RateLimited(val retryAfterSeconds: Int?) : EdgeResult<Nothing>

    /** No connectivity: inputs remain queued locally (기획서 예외 처리: 네트워크 단절). */
    data class Network(val message: String) : EdgeResult<Nothing>

    data class Server(val status: Int, val code: String, val message: String) : EdgeResult<Nothing>
}

@Serializable
internal data class EdgeError(val error: String, val message: String = "")

/**
 * Thin client for Supabase Edge Functions. Sends the user's JWT; the LLM key never
 * exists on the device (기획서 3-① AI 구조화).
 */
class EdgeFunctionClient(
    supabaseUrl: String,
    private val anonKey: String,
    private val accessToken: suspend () -> String?,
    private val http: OkHttpClient,
    private val json: Json = DefaultJson,
) {
    private val functionsBase = supabaseUrl.trimEnd('/').toHttpUrl().newBuilder().addPathSegments("functions/v1").build()

    suspend fun <Req, Res> post(
        function: String,
        body: Req,
        requestSerializer: KSerializer<Req>,
        responseSerializer: KSerializer<Res>,
    ): EdgeResult<Res> {
        val token = accessToken() ?: return EdgeResult.Unauthorized
        val request = Request.Builder()
            .url(functionsBase.newBuilder().addPathSegment(function).build())
            .header("Authorization", "Bearer $token")
            .header("apikey", anonKey)
            .post(json.encodeToString(requestSerializer, body).toRequestBody(JSON_MEDIA))
            .build()

        val response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            return EdgeResult.Network(e.message ?: "network error")
        }
        return response.use { parse(it, responseSerializer) }
    }

    private fun <Res> parse(response: Response, serializer: KSerializer<Res>): EdgeResult<Res> {
        val text = response.body?.string().orEmpty()
        if (response.isSuccessful) {
            return try {
                EdgeResult.Success(json.decodeFromString(serializer, text))
            } catch (e: SerializationException) {
                EdgeResult.SchemaRejected("client decode failed: ${e.message}")
            } catch (e: IllegalArgumentException) {
                EdgeResult.SchemaRejected("client decode failed: ${e.message}")
            }
        }
        val error = runCatching { json.decodeFromString(EdgeError.serializer(), text) }
            .getOrElse { EdgeError("http_${response.code}", text.take(MAX_ERROR_CHARS)) }
        return when (response.code) {
            401 -> EdgeResult.Unauthorized
            403 -> if (error.error == "consent_required") {
                EdgeResult.ConsentRequired
            } else {
                EdgeResult.Server(403, error.error, error.message)
            }
            422 -> EdgeResult.SchemaRejected(error.message)
            429 -> EdgeResult.RateLimited(response.header("Retry-After")?.toIntOrNull())
            else -> EdgeResult.Server(response.code, error.error, error.message)
        }
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_ERROR_CHARS = 300
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response)
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { cancel() }
}
