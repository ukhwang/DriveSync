package com.drivesync.core.ai

import com.drivesync.core.action.ActionExecutor
import com.drivesync.core.action.ExecutionRequest
import com.drivesync.core.action.ExecutionResult
import com.drivesync.core.model.EmailDraft
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExecuteActionRequest(
    val actionId: String,
    val idempotencyKey: String,
    val draft: EmailDraft,
    val draftVersion: Int,
)

@Serializable
enum class ExecuteStatus {
    @SerialName("executed") EXECUTED,
    @SerialName("already_executed") ALREADY_EXECUTED,
}

@Serializable
data class ExecuteActionResponse(val status: ExecuteStatus, val log: String)

/**
 * Sends approved actions to the `execute-action` Edge Function, which performs the real
 * email send. Success is reported ONLY when the server says so (기획서 본선 시연 2).
 */
class HttpActionExecutor(private val client: EdgeFunctionClient) : ActionExecutor {

    override suspend fun execute(request: ExecutionRequest): ExecutionResult {
        val body = ExecuteActionRequest(request.actionId, request.idempotencyKey, request.draft, request.draftVersion)
        return when (
            val result = client.post(
                "execute-action",
                body,
                ExecuteActionRequest.serializer(),
                ExecuteActionResponse.serializer(),
            )
        ) {
            is EdgeResult.Success -> when (result.value.status) {
                ExecuteStatus.EXECUTED -> ExecutionResult.Executed(result.value.log)
                ExecuteStatus.ALREADY_EXECUTED -> ExecutionResult.AlreadyExecuted(result.value.log)
            }
            is EdgeResult.Network -> ExecutionResult.Failed("네트워크 연결 없음 · 다음 정차 때 재시도하세요")
            is EdgeResult.RateLimited -> ExecutionResult.Failed("요청이 많습니다 · 잠시 후 재시도하세요")
            EdgeResult.Unauthorized -> ExecutionResult.Failed("로그인이 만료되었습니다")
            EdgeResult.ConsentRequired -> ExecutionResult.Failed("외부 전송 동의가 필요합니다")
            is EdgeResult.SchemaRejected -> ExecutionResult.Failed("서버 응답 검증 실패: ${result.detail}")
            is EdgeResult.Server -> ExecutionResult.Failed("발송 실패(${result.status}): ${result.message}")
        }
    }
}
