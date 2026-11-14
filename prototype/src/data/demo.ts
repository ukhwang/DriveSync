/**
 * ALL demo content lives in this file (simulated data only).
 * When the Figma design changes wording or items, edit here; screens only render props.
 *
 * Privacy rules (enforced by src/compliance.test.ts):
 * - contact names are masked as "성OO 직함"
 * - emails use the reserved `.example` domain (RFC 2606), never a registrable domain
 */

/** Where a line came from: what the user recorded vs what the AI inferred. */
export type Source = "user" | "ai";

export interface SourcedLine {
  text: string;
  source: Source;
}

export interface Scenario {
  id: "a" | "b" | "c";
  name: string;
  contact: string;
  email: string;
  visitNo: number;
  etaMinutes: number;
  distanceKm: number;
  arrival: string;
}

export const SCENARIOS: readonly Scenario[] = [
  {
    id: "a",
    name: "에이펙스 테크",
    contact: "박OO 팀장",
    email: "purchasing@apex-tech.example",
    visitNo: 3,
    etaMinutes: 18,
    distanceKm: 9.4,
    arrival: "11:00",
  },
  {
    id: "b",
    name: "OO종합병원",
    contact: "김OO 팀장",
    email: "facility@oo-hospital.example",
    visitNo: 3,
    etaMinutes: 18,
    distanceKm: 9.4,
    arrival: "11:00",
  },
  {
    id: "c",
    name: "한빛물류",
    contact: "이OO 팀장",
    email: "fleet@hanbit-logistics.example",
    visitNo: 3,
    etaMinutes: 18,
    distanceKm: 9.4,
    arrival: "11:00",
  },
];

/** ETA-aware briefing length, same thresholds as android/core/session BriefingPlanner. */
export function briefingPlan(etaMinutes: number): { label: string; seconds: number; recap: boolean } {
  if (etaMinutes < 10) return { label: "단거리 브리핑 (30초 분량)", seconds: 30, recap: false };
  if (etaMinutes < 25) return { label: "중거리 브리핑 (1분 분량)", seconds: 60, recap: true };
  return { label: "장거리 브리핑 (2분 분량)", seconds: 120, recap: true };
}

// ------------------------------------------------------------------ 01 출발 전 브리핑

export interface BriefCard {
  label: string;
  title: string;
  detail: string;
  source: Source;
  tone: "cyan" | "emerald";
  labelTone: "amber" | "slate" | "blue";
}

export const BRIEF_CARDS: readonly BriefCard[] = [
  {
    label: "핵심 맥락 · 직전 방문 기록",
    title: "제안 단가 재검토 및 5% 조정 여부 대기",
    detail: "10/28 미팅에서 기존 장비 노후화로 하반기 교체 예산 편성 확인",
    source: "user",
    tone: "cyan",
    labelTone: "amber",
  },
  {
    label: "필수 후속 · 지난 통화 메모",
    title: "내일까지 수정 견적서 재발송 요청",
    detail: "단가표 수정본 빠른 회신이 이번 미팅 신뢰 확보의 핵심",
    source: "user",
    tone: "cyan",
    labelTone: "slate",
  },
  {
    label: "오늘 목표 · AI 추천 전략",
    title: "최종 도입 조건 확정 및 금요일 데모 일정 합의",
    detail: "초기 유지보수 부담 우려 해소 후 다음 의사결정 단계 확정",
    source: "ai",
    tone: "emerald",
    labelTone: "blue",
  },
];

// ------------------------------------------------------------------ 03 단발성 음성 메모

/** "2분" for 120, "45초" for 45. Used everywhere the memo limit is shown. */
export function durationLabel(seconds: number): string {
  if (seconds % 60 === 0) return `${seconds / 60}분`;
  if (seconds > 60) return `${Math.floor(seconds / 60)}분 ${seconds % 60}초`;
  return `${seconds}초`;
}

export const VOICE_MEMO = {
  /** Max length of one voice memo. Setting: change this value only (Kotlin: DRIVESYNC_MEMO_MAX_SECONDS). */
  maxSeconds: 120,
  /** The memo is saved automatically after this much silence. */
  silenceSeconds: 3,
  recordedAt: "10:48",
  /** Shown only after parking (state 04). Never displayed while driving. */
  transcript: "방금 통화했는데 하반기 교체 예산 편성 중이래. 경쟁사 AS 지연 문제 파고들자.",
} as const;

// ------------------------------------------------------------------ 04 미팅 치트시트 (기획서 기준 4종)

export type CheatCardId = "summary" | "concerns" | "questions" | "memos";

export interface CheatCard {
  id: CheatCardId;
  badge: string;
  title: string;
  tone: "cyan" | "emerald" | "amber" | "slate";
  lines: SourcedLine[];
}

export const CHEAT_CARDS: readonly CheatCard[] = [
  {
    id: "summary",
    badge: "① 핵심 3줄",
    title: "오늘 미팅에서 꼭 가져갈 3가지",
    tone: "cyan",
    lines: [
      { text: "하반기 장비 교체 예산이 편성 중이다", source: "user" },
      { text: "단가 5% 조정 여부가 결정 변수다", source: "user" },
      { text: "금요일 데모 일정 확정이 오늘 목표다", source: "ai" },
    ],
  },
  {
    id: "concerns",
    badge: "② 우려사항",
    title: "상대가 망설일 수 있는 지점",
    tone: "amber",
    lines: [
      { text: "초기 유지보수 비용 부담", source: "user" },
      { text: "경쟁사 대비 AS 응답 속도 비교 가능성", source: "ai" },
    ],
  },
  {
    id: "questions",
    badge: "③ 예상 질문",
    title: "미리 답을 준비할 질문",
    tone: "emerald",
    lines: [
      { text: "5% 할인은 3년 약정 조건인가요?", source: "ai" },
      { text: "데모 때 기존 장비 데이터 이전도 볼 수 있나요?", source: "ai" },
    ],
  },
  {
    id: "memos",
    badge: "④ 이동 중 메모",
    title: `주행 중 음성 메모 (${VOICE_MEMO.recordedAt})`,
    tone: "slate",
    lines: [{ text: VOICE_MEMO.transcript, source: "user" }],
  },
];

// ------------------------------------------------------------------ 05 디브리핑 → 후속 작업

export const DEBRIEF = {
  recordedAt: "10:52",
  duration: "00:18",
  text: "신규 장비 3대 도입 긍정 검토, 금요일 오전 10시 최종 데모 확정. 5% 할인 견적서 오늘 중 발송.",
} as const;

export type FollowUpId = "email" | "calendar";

export function emailDraft(s: Scenario): { to: string; subject: string; body: string } {
  return {
    to: s.email,
    subject: `[${s.name}] 5% 할인 견적서 및 금요일 데모 일정 안내`,
    body:
      `${s.contact}님, 오늘 미팅에 시간 내주셔서 감사합니다.\n` +
      "협의한 유지보수 5% 할인 조건을 반영한 수정 견적서를 전달드립니다.\n" +
      "금요일 오전 10시 최종 데모 일정도 함께 확정되었습니다.\n" +
      "추가 문의 사항은 편하게 회신해 주세요.",
  };
}

export const CALENDAR_ITEM = {
  title: "금요일 10:00 최종 데모",
  detail: "캘린더 등록 · 참석자: 담당 영업, 고객 담당자",
} as const;

// ------------------------------------------------------------------ 06 실행 결과

export const OFFLINE_CONTEXT = "지하주차장 진입으로 통신이 끊겨 캘린더 등록이 보류되었습니다.";

export function idempotencyKey(scenario: Scenario, item: FollowUpId, version: number): string {
  return `ds-${scenario.id}-${item}:v${version}`;
}
