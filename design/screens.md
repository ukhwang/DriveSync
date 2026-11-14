# 화면 명세 (단일 원본)

## 7개 시연 상태 (최상위 기준 · 제출·본선 시연용)

웹 프로토타입(`prototype/`)과 [시연 대본](../docs/demo-script.md)은 이 7개 상태를 따릅니다. 문구·데이터는 `prototype/src/data/demo.ts`, 상단 바 문구는 `prototype/src/data/systemBar.ts` **한 곳**에만 있습니다.

| # | 상태 | 차량 상태 (상단 바) | 핵심 요소 | 웹 코드 | Kotlin 세션 상태 | 아래 14화면 |
|---|---|---|---|---|---|---|
| 01 | 출발 전 브리핑 | `CarUxRestrictions 해제 + P단` | 경로 타임라인 바, ETA → 브리핑 분량 자동 매칭, 브리핑 카드 3장(출처 배지) | `State01Briefing.tsx` | IDLE → PREPARE_SESSION | #1–#2 |
| 02 | 이동 중 브리핑 재생 | `CarUxRestrictions 활성 · D단 주행 제한` | 목적지·ETA, 이퀄라이저, 일시정지/계속 · 중지 · 음성 메모 | `State02Driving.tsx` | DRIVING_LIMITED | #3, #5 |
| 03 | 단발성 음성 메모 | 주행 제한 | 원형 음파, 최대 2분(설정으로 변경 가능, 말이 끝나면 자동 저장), 저장 확인. **전사문·AI 분류 없음** | `State03VoiceMemo.tsx` | DRIVING_LIMITED | #4 |
| 04 | 도착 후 미팅 치트시트 | `CarUxRestrictions 해제 + P단` | 3D 원기둥 캐러셀 4장(핵심 3줄·우려사항·예상 질문·이동 중 메모), 상세 팝업·수정. **승인 버튼 없음** | `State04CheatSheet.tsx` | PARKED_REVIEW | #7 |
| 05 | 디브리핑 + 개별 승인 | `CarUxRestrictions 해제 + P단` | 2분할 보드: 디브리핑 원문 / 메일·일정 각각 Draft→Review→Approved | `State05Approval.tsx` | PARKED_REVIEW → AWAITING_APPROVAL | #8–#10 |
| 06 | 실행 결과 + 오프라인 재시도 | `CarUxRestrictions 해제 + P단` | Executed 로그, 지하주차장 Failed → 다시 승인 → Retry → Executed (같은 실행 키) | `State06Result.tsx` | EXECUTING → COMPLETED | #11–#13 |
| 07 | 신호 오류 안전 잠금 | `PAUSED_BY_POLICY · Fail-Safe` | 잠금, 세션 보존 | `State07Locked.tsx` | PAUSED_BY_POLICY | #6 |

웹 코드 경로: `prototype/src/states/`. 표기 원칙은 `prototype/src/compliance.test.tsx`가 검사합니다.

---

## 차량 앱(Kotlin) 14화면 상세

아래는 Kotlin 차량 앱(`android/app`)의 화면 단위 명세입니다. 7개 상태와의 대응은 위 표의 마지막 열을 보세요.
색·크기·글꼴은 [tokens.json](tokens.json)에 있습니다.

## 공통 규칙

- 화면 크기: 1920×720 가로형 차량 디스플레이 (초안 — Pleos 에뮬레이터 확인 후 `tokens.json` `frame`에서 확정)
- 모든 화면 상단: **상태 바** = 차량 상태 칩(`정차 · P` / `주행 제한` / `정책 일시정지`) + 단계 이름 + 오른쪽 `시뮬레이션` 배지
  - 코드: `StatusBar` · `phaseLabel()` — `android/app/.../ui/components/Components.kt`, `ui/DriveSyncRoot.kt`
- 시뮬레이터(운영자) 패널은 앱 오른쪽 220px 세로 띠. **Figma 시연 화면에는 넣지 않음** (`SimulatorPanel`, `ui/DriveSyncRoot.kt`)
- **주행 화면 규칙** (#3–#6, #14): 텍스트 입력·목록·AI 대화·작은 버튼 없음. 허용 요소 = 목적지, ETA, 세션 상태, 큰 메모 버튼, 화면 탭으로 음성 정지
- 출처 배지: 파란 테두리 `내가 말함`, 보라 테두리 `AI 추론 · 확인 필요` (`SourceBadge`)
- 버튼 높이 최소 76, 본문 글자 최소 20

## 화면

코드 경로 접두사: `android/app/src/main/kotlin/com/drivesync/app/ui/`

| # | 이름 | 상태 (세션 / 차량) | 보이는 요소 | 조작 → 다음 | 코드 위치 |
|---|---|---|---|---|---|
| 1 | 방문 목록 | IDLE / 정차 | 왼쪽: "오늘 방문 3건" + 방문 카드 3개(시각·장소·목적). 오른쪽: "방문을 선택하세요" | 방문 카드 탭 → #2 | `screens/ParkedScreens.kt` `VisitListScreen` |
| 2 | 방문 준비 | IDLE→PREPARE_SESSION / 정차 | 왼쪽 목록(선택 강조). 오른쪽: 고객사명·담당자, `내비 목적지 일치` 칩, 브리핑 카드 "핵심 3줄 + 우려사항", "주행 중 음성 브리핑 약 60초 · 도착 5분 전 재요약", `이 방문으로 세션 시작` 버튼 | 세션 시작 → (출발) #3 | `VisitListScreen`, `BriefingCard`, `PrepareScreen` |
| 3 | 주행 · 음성 브리핑 | DRIVING_LIMITED / 주행 제한 | 목적지(큰 글씨), `18분 후 도착`(가장 큰 글씨, 파랑), "방문 세션 진행 중 · 메모 0건", `음성 브리핑 중 · 화면을 탭하면 정지` 칩, 오른쪽 원형 `음성 메모` 버튼(200) | 메모 버튼 → #4 / 화면 탭 → 음성 정지 | `screens/DrivingScreens.kt` `DrivingScreen` |
| 4 | 주행 · 음성 메모 | DRIVING_LIMITED / 주행 제한 | #3과 같음, 메모 버튼이 빨강 `듣는 중…` → 초록 `저장됨` | 자동(2초) → #3 | `DrivingScreen` (`MemoCapture`) |
| 5 | 도착 임박 재요약 | ARRIVAL_PENDING / 주행 제한 | `4분 후 도착`, "곧 도착 · 정차하면 치트시트가 열립니다", 브리핑 칩(15초 재요약) | 정차 → #7 | `DrivingScreen`, `drivingStatus()` |
| 6 | Fail-Safe | PAUSED_BY_POLICY / 신호 끊김 | `Fail-Safe` 빨강 칩, "차량 상태를 확인할 수 없습니다", 설명 1줄, "원인: 차량 상태 수신 실패" | 신호 복구 → #3 | `DrivingScreens.kt` `FailSafeScreen`, `failSafeText()` |
| 7 | 미팅 치트시트 | PARKED_REVIEW / 정차 · P | 제목 "○○ 미팅 치트시트" + `미팅 종료 · 디브리핑` 버튼. 왼쪽: 카드 `핵심 3줄`, `우려사항` (줄마다 출처 배지). 오른쪽: `예상 질문`, `이동 중 메모`([음성 10:12] …) | 미팅 종료 → #8 | `ParkedScreens.kt` `CheatSheetScreen` |
| 8 | 디브리핑 | PARKED_REVIEW / 정차 | "미팅 디브리핑", 안내 1줄, 큰 입력창(예시 문구), `취소`, `후속 업무 추출` | 추출 → #9 | `DebriefScreen` |
| 9 | 후속 작업 (Draft) | AWAITING_APPROVAL / 정차 | "후속 작업 승인" + `세션 종료 · 남은 작업 이월`, 안내 1줄, 이메일 카드: 수신자·`Draft · 초안` 칩·제목·본문·`검토 시작` | 검토 시작 → #10 | `ApprovalScreen`, `ActionCard` |
| 10 | 검토·승인 | AWAITING_APPROVAL / 정차 | 이메일 카드 `Review · 검토 중`, 제목·본문 편집창, `승인` | 승인 → #11 | `ActionCard` (REVIEW) |
| 11 | 발송 완료 | EXECUTING→AWAITING/COMPLETED | `Approved · 실행 대기` → `실행 · 발송` → `Executed · 발송 완료`(초록 테두리), "서버 결과: sent …" | → #13 / 실패 분기 #12 | `ActionCard` (APPROVED, EXECUTED) |
| 12 | 실패 → 재시도 | AWAITING_APPROVAL / 정차 | `Failed · 실패`(빨강 테두리), "실패 원인: 네트워크 연결 없음 · 다음 정차 때 재시도하세요", `다음 정차 때 재시도` → `Retry · 재승인 필요` + `다시 승인` | 다시 승인 → #11 | `ActionCard` (FAILED, RETRY) |
| 13 | 방문 리포트 | COMPLETED / 정차 | "○○ 방문 리포트", `기기에 암호화 저장됨 (SQLCipher)` 칩, 카드 3개(실행 완료 n건 / 다음 방문으로 이월 n건 / 메모 n건), `다음 방문 선택` | → #1 | `ReportScreen` |
| 14 | 승인 보류 | AWAITING_APPROVAL / 주행 제한 | #3 형태, 상태 문구 "승인 대기 작업은 정차 후 확인합니다", 단계 이름 "5 · 승인 보류(주행 중)" | 정차 → #9 | `DrivingScreen`, `phaseLabel()` |

## 시연용 예시 데이터 (가상)

| 고객사 | 담당자 | 방문 목적 | 이력 |
|---|---|---|---|
| 한빛메디칼 (본사) | 김도윤 구매팀장 | 2차 견적 협의 · 납기 조건 확정 | 납기 4주 이내 요구, 경쟁사 대비 8% 높다는 피드백 |
| 서연정형외과 | 박서연 원장 | 신규 소개 미팅 | 첫 방문 |
| 다온제약 물류센터 | 이준호 과장 | 시범 운영 리뷰 · 계약서 전달 | 계약서 초안 미전달(이월 작업) |

코드: `android/app/.../DemoSeed.kt`, `supabase/seed.sql` — 바꾸면 두 곳 모두 수정.
