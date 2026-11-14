package com.drivesync.core.ai

import com.drivesync.core.action.ExecutionRequest
import com.drivesync.core.action.ExecutionResult
import com.drivesync.core.model.BriefingTier
import com.drivesync.core.model.EmailDraft
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EdgeFunctionAiGatewayTest {

    private lateinit var server: MockWebServer
    private lateinit var client: EdgeFunctionClient

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = EdgeFunctionClient(
            supabaseUrl = server.url("/").toString(),
            anonKey = "anon",
            accessToken = { "user-jwt" },
            http = OkHttpClient(),
        )
    }

    @AfterTest
    fun tearDown() = server.shutdown()

    private fun line(text: String, source: String = "ai") = """{"text":"$text","source":"$source"}"""

    @Test
    fun `valid insight passes and sends auth headers`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"summary":[${line("a", "user")},${line("b")},${line("c")}],"concerns":[],"expectedQuestions":[${line("q")}]}""",
            ),
        )
        val result = EdgeFunctionAiGateway(client).insight(InsightRequest("v1", listOf("메모")))
        assertIs<EdgeResult.Success<*>>(result)
        val recorded = server.takeRequest()
        assertEquals("/functions/v1/generate-insight", recorded.path)
        assertEquals("Bearer user-jwt", recorded.getHeader("Authorization"))
        assertEquals("anon", recorded.getHeader("apikey"))
    }

    @Test
    fun `insight with two summary lines is rejected on the client`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"summary":[${line("a")},${line("b")}],"concerns":[],"expectedQuestions":[]}"""),
        )
        val result = EdgeFunctionAiGateway(client).insight(InsightRequest("v1", emptyList()))
        assertIs<EdgeResult.SchemaRejected>(result)
    }

    @Test
    fun `briefing tier mismatch is rejected`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tier":"long","screenLines":["x"],"voiceScript":[{"text":"x","core":true}],"preArrivalRecap":"r"}""",
            ),
        )
        val result = EdgeFunctionAiGateway(client).briefing(BriefingRequest("v1", 5, BriefingTier.SHORT))
        assertIs<EdgeResult.SchemaRejected>(result)
    }

    @Test
    fun `server error codes map to explicit results`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"consent_required","message":"동의 필요"}"""))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30").setBody("""{"error":"rate_limited"}"""))
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"schema_rejected","message":"bad llm output"}"""))
        val gateway = EdgeFunctionAiGateway(client)
        val request = InsightRequest("v1", emptyList())
        assertEquals(EdgeResult.ConsentRequired, gateway.insight(request))
        assertEquals(EdgeResult.RateLimited(30), gateway.insight(request))
        assertEquals(EdgeResult.SchemaRejected("bad llm output"), gateway.insight(request))
    }

    @Test
    fun `network drop becomes a failed execution never a success`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val result = HttpActionExecutor(client).execute(
            ExecutionRequest("a1", "a1:v1", EmailDraft("buyer@test.example", "s", "b"), 1),
        )
        assertIs<ExecutionResult.Failed>(result)
        assertTrue(result.reason.contains("네트워크"))
    }

    @Test
    fun `already executed response is honoured`() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"already_executed","log":"resend id=9"}"""))
        val result = HttpActionExecutor(client).execute(
            ExecutionRequest("a1", "a1:v1", EmailDraft("buyer@test.example", "s", "b"), 1),
        )
        assertEquals(ExecutionResult.AlreadyExecuted("resend id=9"), result)
        assertTrue(server.takeRequest().body.readUtf8().contains("\"idempotencyKey\":\"a1:v1\""))
    }
}
