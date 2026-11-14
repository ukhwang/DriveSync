# 백엔드 (Supabase)

## 구성

| 함수 | 입력 → 출력 | 주요 검증 |
|---|---|---|
| `generate-briefing` | `{visitId, etaMinutes, tier}` → `Briefing` | 동의, 사용량(30/시간), tier 일치, core 문장 ≥ 1, tier별 재요약 유무 |
| `generate-insight` | `{visitId, travelNotes}` → `Insight` | 동의, 사용량, 핵심 정확히 3줄, 항목별 출처(user/ai). 검증 통과분만 저장 |
| `extract-actions` | `{sessionId, visitId, debrief}` → `{decisions, actions}` | 동의, 사용량(20/시간). 초안만 저장, 발송 없음 |
| `execute-action` | `{actionId, idempotencyKey, draft, draftVersion}` → `{status, log}` | 본인 작업, 실행 키 = `actionId:v{draftVersion}`, 수신 도메인 허용 목록, 키당 1회 |
| `record-consent` | `{granted}` → `boolean` | 로그인 |

계약은 [`_shared/schemas.ts`](../supabase/functions/_shared/schemas.ts)의 Zod 스키마가 원본이며 `deno task schemas`로 [`/schemas`](../schemas)의 JSON Schema를 생성합니다. 테스트가 동기화를 검사합니다.

## LLM

- 공식 SDK `@anthropic-ai/sdk` + 구조화 출력(Zod → 제약된 JSON), 기본 모델 `claude-opus-5-5`(`LLM_MODEL`로 변경 가능).
- 정책상 거절 시 같은 호출 안에서 대체 모델로 재시도하는 서버 측 fallback(`fallbacks: "default"`)을 켰습니다.
- 출력은 SDK 파싱 후 Zod로 한 번 더 검증하고, 실패하면 422로 응답하며 저장하지 않습니다.
- 고객 맥락은 `<customer_context>`/`<notes>` 태그 안의 "자료"로 전달하고, 그 안의 지시문은 따르지 않도록 지시합니다.

## 로컬 실행

```bash
supabase start
```

```bash
cp supabase/functions/.env.example supabase/functions/.env
```

```bash
supabase functions serve --env-file supabase/functions/.env
```

에뮬레이터에서 호스트 PC의 로컬 Supabase는 `http://10.0.2.2:54321`입니다.

## 호스팅 프로젝트 배포

```bash
supabase link --project-ref <project-ref>
```

```bash
supabase db push
```

```bash
supabase secrets set --env-file supabase/functions/.env
```

```bash
supabase functions deploy
```

호스팅 프로젝트에는 `seed.sql`의 데모 계정을 쓰지 말고, 대시보드에서 사용자를 만든 뒤 그 사용자 ID로 고객·방문 데이터를 넣으세요.

## 본선 시연 설정

- `RESEND_API_KEY`, `MAIL_FROM`(인증된 발신 도메인), `ALLOWED_RECIPIENT_DOMAINS`(시연용 수신 도메인만 허용)를 설정합니다.
- 시드 고객 이메일은 `*.example` 가상 주소입니다. 실제 수신함 시연 시 담당자 이메일을 시연용 주소로 바꾸세요.
