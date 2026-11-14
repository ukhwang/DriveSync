import type { Scenario } from "./demo";

/**
 * Top status bar wording for the 7 demo states, in ONE place.
 * Rule (proposal 1-①): parked screens must show BOTH the UX restriction release and the P gear.
 * The screen permission comes from CarUxRestrictions alone; P gear is shown as confirmation.
 */
export const PARKED_LABEL = "CarUxRestrictions 해제 + P단";
export const DRIVING_LABEL = "CarUxRestrictions 활성 · D단 주행 제한";
export const LOCKED_LABEL = "PAUSED_BY_POLICY · Fail-Safe 안전 잠금";

export type BarMode = "parked" | "driving" | "locked";

export interface SystemBarConfig {
  mode: BarMode;
  safety: string;
  /** Work-mode variant (states 04 and 05) uses the Figma "work mode" bar style. */
  workLabel?: string;
  nav: (s: Scenario) => string;
  /** Screens that show fictional customers/data must carry the simulation badge. */
  simulated: boolean;
}

export const STATE_LABELS = [
  "출발 전 브리핑",
  "이동 중 재생",
  "음성 메모",
  "도착 후 치트시트",
  "디브리핑 · 승인",
  "실행 결과",
  "안전 잠금",
] as const;

export const SYSTEM_BARS: readonly SystemBarConfig[] = [
  {
    mode: "parked",
    safety: PARKED_LABEL,
    nav: (s) => `NaviHelper 목적지 매칭 · ${s.name} · ${s.arrival} 도착 예정`,
    simulated: true,
  },
  {
    mode: "driving",
    safety: DRIVING_LABEL,
    nav: (s) => `목적지: ${s.name} · ETA 14분 · 자동 음성 브리핑`,
    simulated: true,
  },
  {
    mode: "driving",
    safety: DRIVING_LABEL,
    nav: (s) => `목적지: ${s.name} · ETA 12분 · 음성 메모 기록 중`,
    simulated: true,
  },
  {
    mode: "parked",
    safety: PARKED_LABEL,
    workLabel: "미팅 치트시트",
    nav: (s) => `${s.name} 미팅 치트시트 (${s.visitNo}회차 방문)`,
    simulated: true,
  },
  {
    mode: "parked",
    safety: PARKED_LABEL,
    workLabel: "후속 작업 개별 승인",
    nav: () => "후속 작업 검토 및 개별 승인 (Human-in-the-Loop)",
    simulated: true,
  },
  {
    mode: "parked",
    safety: PARKED_LABEL,
    nav: () => "실행 결과 및 오프라인 재시도",
    simulated: true,
  },
  {
    mode: "locked",
    safety: LOCKED_LABEL,
    nav: () => "차량 상태 신호 확인 중 · 조작 잠금",
    // Lock screen shows no customer data.
    simulated: false,
  },
];
