import type { Source } from "../data/demo";

export const SOURCE_LABEL: Record<Source, string> = {
  user: "내 기록",
  ai: "AI 추론 · 확인 필요",
};

/** Origin badge required on every briefing / cheat-sheet / draft item (proposal 3-②). */
export function SourceBadge({ source }: { source: Source }) {
  return (
    <span className={`source-badge ${source}`} data-source={source}>
      {SOURCE_LABEL[source]}
    </span>
  );
}

export const SIMULATION_LABEL = "시뮬레이션 데이터";

export function SimBadge() {
  return <span className="sim-badge">{SIMULATION_LABEL}</span>;
}
