package com.drivesync.core.ai

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SupabaseAuthTest {

    private class MemoryStorage : TokenStorage {
        var session: AuthSession? = null
        override fun load() = session
        override fun save(session: AuthSession) { this.session = session }
        override fun clear() { session = null }
    }

    @Test
    fun `signs in and refreshes an expiring token`() = runTest {
        val server = MockWebServer().apply { start() }
        val storage = MemoryStorage()
        var now = 1_000L
        val auth = SupabaseAuth(server.url("/").toString(), "anon", storage, OkHttpClient()) { now }

        server.enqueue(MockResponse().setBody("""{"access_token":"a1","refresh_token":"r1","expires_at":4600}"""))
        assertIs<AuthResult.SignedIn>(auth.signIn("demo@drivesync.example", "pw"))
        assertEquals("/auth/v1/token?grant_type=password", server.takeRequest().path)
        assertEquals("a1", auth.accessToken())

        now = 4_590L
        server.enqueue(MockResponse().setBody("""{"access_token":"a2","refresh_token":"r2","expires_at":8200}"""))
        assertEquals("a2", auth.accessToken())
        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)

        now = 9_000L
        server.enqueue(MockResponse().setResponseCode(400))
        assertNull(auth.accessToken())
        server.shutdown()
    }
}
