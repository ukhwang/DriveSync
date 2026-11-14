# 상태 머신

구현: [`SessionStateMachine`](../android/core/session/src/main/kotlin/com/drivesync/core/session/SessionStateMachine.kt) · 테스트: `SessionStateMachineTest`

## 세션 (10 상태)

```mermaid
stateDiagram-v2
  [*] --> IDLE
  IDLE --> PREPARE_SESSION: 방문 선택 + 목적지 일치 확인 (정차 중에만)
  PREPARE_SESSION --> DRIVING_LIMITED: 주행 제한 활성
  DRIVING_LIMITED --> ARRIVAL_PENDING: ETA ≤ 5분
  DRIVING_LIMITED --> PARKED_REVIEW: ETA 미지원 + 제한 해제 + P단
  ARRIVAL_PENDING --> PARKED_REVIEW: 제한 해제 + P단
  PARKED_REVIEW --> AWAITING_APPROVAL: 디브리핑 완료 (초안 ≥ 1)
  PARKED_REVIEW --> COMPLETED: 디브리핑 완료 (초안 0) / 세션 종료
  AWAITING_APPROVAL --> EXECUTING: 승인된 작업 실행 (정차 중, 보류 아님)
  EXECUTING --> AWAITING_APPROVAL: 미완료(실패·미승인) 남음
  EXECUTING --> COMPLETED: 모두 실행됨
  AWAITING_APPROVAL --> COMPLETED: 세션 종료 (남은 작업은 다음 방문 브리핑으로 이월)
  COMPLETED --> PREPARE_SESSION: 다음 방문
  PREPARE_SESSION --> PAUSED_BY_POLICY: 신호 불확실
  DRIVING_LIMITED --> PAUSED_BY_POLICY: 신호 불확실
  ARRIVAL_PENDING --> PAUSED_BY_POLICY: 신호 불확실
  PAUSED_BY_POLICY --> DRIVING_LIMITED: 신호 복구 → 이전 상태 (즉시 재평가)
  ERROR --> AWAITING_APPROVAL: 로컬 스냅샷 복구 (EXECUTING이었다면)
```

- `AWAITING_APPROVAL` 중 주행이 시작되면 상태는 유지하고 `approvalsOnHold = true` (승인 보류). 실행 요청은 거부됩니다.
- `PARKED_REVIEW`, `AWAITING_APPROVAL`에서도 신호가 불확실하면 `PAUSED_BY_POLICY`로 갑니다.
- 어떤 상태든 `Fatal` → `ERROR`. 복구는 `RecoveredFromLocal(snapshot)`.

## 후속 작업 (6 상태)

구현: [`ActionTransitions`](../android/core/action/src/main/kotlin/com/drivesync/core/action/ActionEngine.kt) · 서버: [`execute-action`](../supabase/functions/execute-action/handler.ts)

```mermaid
stateDiagram-v2
  [*] --> Draft: 디브리핑에서 AI 초안 생성
  Draft --> Review: 정차 중 검토 시작
  Review --> Review: 초안 수정 (draftVersion+1, 새 실행 키)
  Review --> Approved: 사용자 승인 (정차 중에만)
  Approved --> Executed: 서버가 발송 확인
  Approved --> Failed: 서버 실패 응답 / 네트워크 단절
  Failed --> Retry: 다음 정차 때 재시도 안내
  Retry --> Approved: 다시 승인 (같은 초안 = 같은 실행 키)
  Retry --> Review: 수정 후 재검토
  Executed --> [*]
```

| 불변식 | 강제 위치 |
|---|---|
| 승인 안 된 작업은 실행기에 도달하지 않음 | `ActionEngine.execute` + 서버 실행 키 검증 |
| `Executed`는 서버 성공 응답으로만 | `ActionEngine` / `HttpActionExecutor` |
| 같은 실행 키는 한 번만 발송 | `action_executions` PK + Resend `Idempotency-Key` |
| 클라이언트는 `actions.status`를 직접 쓸 수 없음 | RLS (select/delete만 허용) |
