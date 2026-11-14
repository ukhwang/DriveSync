# 주행 안전 정책

구현: [`SafetyPolicy`](../android/core/safety/src/main/kotlin/com/drivesync/core/safety/SafetyPolicy.kt) · 테스트: `SafetyPolicyTest`

## 판단 기준

1. **단일 기준**: `CarUxRestrictions.isRequiresDistractionOptimization()`. 기어 P만으로는 아무것도 해제하지 않습니다.
2. **Fail-Safe**: 신호가 없음 / 미지원 / 수신 실패 / 권한 거부 / 5초 이상 갱신 없음 → 주행 제한으로 간주.
3. **Heartbeat**: 차량 어댑터는 1초마다 현재 제한 상태를 다시 읽어 보냅니다. 서비스가 멈추면 타임스탬프가 낡아 자동으로 Fail-Safe가 됩니다.
4. **화면 라우팅**: `DriveSyncRoot`는 세션 단계보다 정책을 먼저 봅니다. 제한 상태면 어떤 작업 화면이든 최소 화면으로 바뀝니다.

## 기능 허용 매트릭스

| 기능 (`Feature`) | 주행 제한 | 정차 (제한 해제) | 비고 |
|---|:---:|:---:|---|
| 목적지·ETA (`DESTINATION_ETA`) | ✅ | ✅ | |
| 세션 상태 (`SESSION_STATUS`) | ✅ | ✅ | |
| 음성 정지 (`STOP_AUDIO`) | ✅ | ✅ | 화면 탭 한 번 |
| 일방향 음성 브리핑 (`ONEWAY_TTS_BRIEFING`) | ✅* | ✅* | *TTS 권한 있을 때 |
| 단발성 음성 메모 (`ONE_SHOT_VOICE_MEMO`) | ⚠️ | ✅* | 주행 중엔 STT 권한 + DO 허용 둘 다 확인 시에만 (현재 off) |
| 브리핑 카드 (`BRIEFING_CARD`) | ❌ | ✅ | |
| 치트시트 (`CHEAT_SHEET`) | ❌ | ✅ | P단 확인 필요. 기어 신호 미지원 차량은 제한 해제만으로 |
| 텍스트 입력 (`TEXT_INPUT`) | ❌ | ✅ | |
| 목록 탐색 (`LIST_BROWSING`) | ❌ | ✅ | |
| AI 대화 (`AI_CONVERSATION`) | ❌ | ✅ | |
| 디브리핑 (`DEBRIEF`) | ❌ | ✅ | |
| 작업 검토·승인 (`ACTION_REVIEW`, `ACTION_APPROVAL`) | ❌ | ✅ | 주행 시작 시 승인 보류 |

`SafetyPolicyTest`의 `no forbidden feature is reachable in any limited or fail-safe context`가 P0-2 완료 기준 "제한 상태에서 금지 기능 접근 0건"을 검증합니다.

## 운전자 우선권

- 음성 브리핑 중 화면 아무 곳이나 탭하면 즉시 정지 (`stopAudio`).
- 내비 안내음 등으로 오디오 포커스를 잃으면 일시정지 후, 끊긴 문장부터 다시 재생.
- 새 음성이 도착하면 겹치지 않고 대기열에 추가.

## 표현 원칙

실차 검증 전까지 "사고 위험 감소"라고 쓰지 않습니다. "플랫폼 주행 제한 정책 준수"라고만 씁니다.
