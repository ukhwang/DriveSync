package com.drivesync.core.ai

import com.drivesync.core.model.ActionStatus
import com.drivesync.core.model.ActionType
import com.drivesync.core.model.Briefing
import com.drivesync.core.model.BriefingTier
import com.drivesync.core.model.EmailDraft
import com.drivesync.core.model.FollowUpAction
import com.drivesync.core.model.Insight
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

// ---- Wire contracts. Single source of truth: /schemas/*.schema.json (mirrored by Zod on the server). ----

@Serializable
data class BriefingRequest(val visitId: String, val etaMinutes: Int, val tier: BriefingTier)

@Serializable
data class InsightRequest(val visitId: String, val travelNotes: List<String>)

@Serializable
data class ExtractActionsRequest(val sessionId: String, val visitId: String, val debrief: String)

@Serializable
data class ExtractedAction(val id: String, val type: ActionType, val draft: EmailDraft)

@Serializable
data class ExtractActionsResponse(val decisions: List<String>, val actions: List<ExtractedAction>)

@Serializable
data class ConsentRequest(val granted: Boolean)

/** AI Gateway (기획서 3-③-4). All LLM work goes through Supabase Edge Functions. */
interface AiGateway {
    suspend fun briefing(request: BriefingRequest): EdgeResult<Briefing>
    suspend fun insight(request: InsightRequest): EdgeResult<Insight>
    suspend fun extractActions(request: ExtractActionsRequest, customerId: String): EdgeResult<List<FollowUpAction>>
    suspend fun recordConsent(granted: Boolean): EdgeResult<Unit>
}

class EdgeFunctionAiGateway(private val client: EdgeFunctionClient) : AiGateway {

    override suspend fun briefing(request: BriefingRequest): EdgeResult<Briefing> =
        client.post("generate-briefing", request, BriefingRequest.serializer(), Briefing.serializer())
            .validate { ContractValidator.briefing(it, request.tier) }

    override suspend fun insight(request: InsightRequest): EdgeResult<Insight> =
        client.post("generate-insight", request, InsightRequest.serializer(), Insight.serializer())
            .validate(ContractValidator::insight)

    override suspend fun extractActions(
        request: ExtractActionsRequest,
        customerId: String,
    ): EdgeResult<List<FollowUpAction>> {
        val result = client.post(
            "extract-actions",
            request,
            ExtractActionsRequest.serializer(),
            ExtractActionsResponse.serializer(),
        ).validate(ContractValidator::extractedActions)
        if (result !is EdgeResult.Success) return result.mapFailure()
        return EdgeResult.Success(
            result.value.actions.map {
                FollowUpAction(
                    id = it.id,
                    sessionId = request.sessionId,
                    customerId = customerId,
                    type = it.type,
                    draft = it.draft,
                    status = ActionStatus.DRAFT,
                )
            },
        )
    }

    override suspend fun recordConsent(granted: Boolean): EdgeResult<Unit> {
        val result = client.post("record-consent", ConsentRequest(granted), ConsentRequest.serializer(), Boolean.serializer())
        return if (result is EdgeResult.Success) EdgeResult.Success(Unit) else result.mapFailure()
    }
}

private inline fun <T> EdgeResult<T>.validate(check: (T) -> List<String>): EdgeResult<T> {
    if (this !is EdgeResult.Success) return this
    val problems = check(value)
    return if (problems.isEmpty()) this else EdgeResult.SchemaRejected(problems.joinToString("; "))
}

@Suppress("UNCHECKED_CAST")
private fun <T> EdgeResult<*>.mapFailure(): EdgeResult<T> {
    check(this !is EdgeResult.Success) { "mapFailure called on success" }
    return this as EdgeResult<T>
}
