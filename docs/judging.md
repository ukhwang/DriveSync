# 심사 기준별 근거 맵

> 기획서에 쓴 약속을 **말이 아니라 코드와 자동 테스트로** 보여줍니다. 아래 테스트는 GitHub에 push할 때마다 자동 실행됩니다 ([Actions](https://github.com/ukhwang/DriveSync/actions)).

코드 경로 약어: `core/…` = `android/core/…/src/main/kotlin/com/drivesync/core/…`, 테스트는 같은 모듈의 `src/test`. 서버 테스트는 [`supabase/functions/tests/handlers_test.ts`](../supabase/functions/tests/handlers_test.ts).

## 1. 차량 특화성 — "스마트폰과 무엇이 다른가?"

| 주장 | 근거 코드 | 증명하는 테스트 | 시연 # |
|---|---|---|---|
| 화면·입력 허용 범위는 CarUxRestrictions **하나로** 결정 | [`SafetyPolicy`](../android/core/safety/src/main/kotlin/com/drivesync/core/safety/SafetyPolicy.kt) | `SafetyPolicyTest` › no forbidden feature is reachable in any limited or fail-safe context | 3 |
| P단만으로는 해제하지 않음 | 같은 파일 | `SafetyPolicyTest` › P gear alone never unlocks when restriction is active | 7 |
| 신호 끊김·멈춤 → 자동 Fail-Safe (1초 heartbeat) | [`CarApiVehicleContextSource`](../android/app/src/main/kotlin/com/drivesync/app/vehicle/CarApiVehicleContextSource.kt), [`MockVehicleContextSource`](../android/core/vehicle/src/main/kotlin/com/drivesync/core/vehicle/MockVehicleContextSource.kt) | `VehicleContextSourceTest` › frozen heartbeat keeps the old timestamp…, `SessionStateMachineTest` › signal loss pauses the session… | 6 |
| ETA에 맞춘 브리핑 분량 (30초 / 1분 / 2분, 도착 5분 전 재요약) | [`BriefingPlanner`](../android/core/session/src/main/kotlin/com/drivesync/core/session/BriefingPlanner.kt) | `BriefingPlannerTest` › ETA tiers follow the proposal table, only core sentences remain when ETA shrinks | 2, 5 |
| 운전자 우선권: 탭 한 번 정지, 내비 안내음에 양보, 겹쳐 재생 안 함 | [`AndroidSpeechOutput`](../android/core/voice/src/main/kotlin/com/drivesync/core/voice/AndroidSpeechOutput.kt) | (실기기 확인 항목 — [roadmap](roadmap.md) P0-6) | 3 |

## 2. 창의성 — "Gleo에게 시키면 되지 않나?"

| 주장 | 근거 코드 | 증명하는 테스트 | 시연 # |
|---|---|---|---|
| 묻지 않아도 차량 이벤트로 5단계 세션이 스스로 진행 | [`SessionStateMachine`](../android/core/session/src/main/kotlin/com/drivesync/core/session/SessionStateMachine.kt) | `SessionStateMachineTest` › happy path walks all five visit stages | 1→13 |
| 도착 판단 = 제한 해제 **+** P단 | 같은 파일 | `SessionStateMachineTest` › arrival requires restriction lifted AND park confirmed | 7 |
| 주행 시작 시 승인 보류 | 같은 파일 | `SessionStateMachineTest` › driving away during approval holds approvals and blocks execution | 14 |
| 고객별 이력·못 끝낸 작업이 다음 브리핑에 자동 주입 | [`prompts.ts`](../supabase/functions/_shared/prompts.ts) `contextBlock`, [`supabase.ts`](../supabase/functions/_shared/supabase.ts) `visitContext` | (시드 데이터 `다온제약` 이월 작업으로 시연) | 2 |

## 3. 기술 활용성 — "AI를 믿을 수 있나? 키는 안전한가?"

| 주장 | 근거 코드 | 증명하는 테스트 | 시연 # |
|---|---|---|---|
| LLM 키는 서버에만, 앱은 서버 함수만 호출 | [`llm.ts`](../supabase/functions/_shared/llm.ts), [`EdgeFunctionClient`](../android/core/ai/src/main/kotlin/com/drivesync/core/ai/EdgeFunctionClient.kt) | `EdgeFunctionAiGatewayTest` › valid insight passes and sends auth headers | 8 |
| 스키마 검증을 통과한 AI 결과만 표시 (서버 + 앱 이중 검증) | [`schemas.ts`](../supabase/functions/_shared/schemas.ts), [`ContractValidator`](../android/core/ai/src/main/kotlin/com/drivesync/core/ai/ContractValidator.kt) | 서버: insight: output with 2 summary lines is rejected (422) and NOT stored · 앱: insight with two summary lines is rejected on the client | 7 |
| 치트시트 줄마다 출처(내가 말함 / AI 추론) | `Insight` 스키마 `source` 필드 | 서버: insight: valid output is stored and returned | 7 |
| 승인 없이는 발송 0건 | [`ActionEngine`](../android/core/action/src/main/kotlin/com/drivesync/core/action/ActionEngine.kt), [`execute-action`](../supabase/functions/execute-action/handler.ts) | `ActionEngineTest` › unapproved actions never reach the executor · 서버: extract-actions stores drafts only and sends nothing | 9–10 |
| 같은 메일 두 번 발송 0건 (실행 키) | `action_executions` PK, Resend `Idempotency-Key` | `ActionEngineTest` › executed action can never execute again · 서버: execute: … never sent twice | 11 |
| 실패를 성공으로 표시하지 않음, Failed → Retry | 같은 파일 | `ActionEngineTest` › failure is never shown as success and retry reuses the same key · 서버: provider failure is 502 + failed, retry… · `EdgeFunctionAiGatewayTest` › network drop becomes a failed execution never a success | 12 |
| 고객 정보 외부 전송 전 동의 | [`guards.ts`](../supabase/functions/_shared/guards.ts) | 서버: requires LLM consent and records it | — |
| 로컬 암호화 저장 + Keystore | [`DriveSyncDatabase`](../android/core/data/src/main/kotlin/com/drivesync/core/data/DriveSyncDatabase.kt), [`KeystorePassphraseProvider`](../android/core/data/src/main/kotlin/com/drivesync/core/data/KeystorePassphraseProvider.kt) | (실기기 확인 항목) | 13 |

## 4. 사업성 — "실제로 쓰고 돈을 낼까?"

| 주장 | 근거 |
|---|---|
| 검증되지 않은 수치는 가설로만 표기 | [기획서 4장](planning/proposal.md#4-사업성) |
| 검증 절차가 실행 가능한 형태로 준비됨 | [인터뷰 가이드·결과표](interviews/README.md), [로드맵·검증 일정](roadmap.md) |
| 원가 가정(방문당 LLM 3회)이 실제 구조와 일치 | 서버 함수가 정확히 3종(브리핑·치트시트·디브리핑 추출), 사용자별 사용량 제한 [`guards.ts`](../supabase/functions/_shared/guards.ts) `QUOTAS` |
| 쌓이는 고객별 방문 이력 = 잠금 효과 | [`20261114000000_init.sql`](../supabase/migrations/20261114000000_init.sql) customers·visits·actions, RLS로 사용자별 격리 |

## 발표 Q&A 대비

**Q1. 주행 중에 앱이 화면을 띄우는 것 자체가 위험하지 않나요?**
A. 판단은 플랫폼의 CarUxRestrictions가 하고, 앱은 그 상태에서 허용되는 기능만 그립니다. 신호를 믿을 수 없으면 주행 제한으로 간주합니다. 다만 실차 검증 전이므로 "사고 위험 감소"가 아니라 "플랫폼 주행 제한 정책 준수"라고만 말합니다.

**Q2. AI가 엉뚱한 메일을 보내면요?**
A. 보낼 수 없습니다. AI는 초안만 만들고, 사람이 정차 중 하나씩 검토·승인해야 서버가 발송합니다. 승인 없는 발송 0건은 테스트로 매번 확인합니다.

**Q3. 네트워크가 끊기면요?**
A. 메모와 디브리핑 원문은 기기에 먼저 암호화 저장됩니다. 발송은 실패로 남고 다음 정차 때 다시 승인합니다. 같은 실행 키를 쓰므로 재시도해도 두 번 발송되지 않습니다. (시연 #12)

**Q4. 에뮬레이터 화면이 진짜 차량 데이터인가요?**
A. 아닙니다. 시뮬레이션 데이터가 쓰인 화면에는 항상 `시뮬레이션` 배지가 붙습니다. 실제 차량 API 연결 코드(`CarApiVehicleContextSource`)도 함께 있으며 설정 하나로 전환합니다.

**Q5. 아직 안 된 것은요?**
A. Pleos 전용 SDK(NaviHelper·STT·TTS)는 공모전 포털에서 받은 뒤 연결합니다. 그 전까지는 표준 Android API와 시뮬레이터로 동작합니다. 남은 일은 [roadmap](roadmap.md)에 단계별로 공개되어 있습니다.
