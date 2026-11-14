import { useMemo, useState } from "react";
import type { Scenario } from "../data/demo";
import { Icon } from "../components/Icon";

type Playback = "playing" | "paused" | "stopped";

function Equalizer({ active }: { active: boolean }) {
  const bars = useMemo(() => [32, 48, 70, 42, 76, 98, 58, 86, 52, 74, 100, 64, 46, 82, 58, 36, 68, 44], []);
  return (
    <div className={`equalizer ${active ? "" : "paused"}`} aria-hidden="true">
      {bars.map((height, index) => (
        <i key={index} style={{ height, animationDelay: `${index * -0.08}s` }} />
      ))}
    </div>
  );
}

const STATUS: Record<Playback, string> = {
  playing: "자동 음성 브리핑 재생 중",
  paused: "자동 재생 일시정지",
  stopped: "음성 브리핑 중지됨",
};

/**
 * 02 이동 중 브리핑 재생 — driving minimum mode.
 * One-way TTS only; pause/resume and stop are separate, large controls (운전자 우선권).
 */
export function State02Driving({ scenario, onVoiceMemo }: { scenario: Scenario; onVoiceMemo: () => void }) {
  const [playback, setPlayback] = useState<Playback>("playing");
  return (
    <main className="driving-minimal">
      <section className="minimal-route">
        <div className="driving-restriction">
          <Icon name="shield" size={28} />
          <span>D 기어 · CarUxRestrictions 활성</span>
        </div>
        <span className="route-label">목적지</span>
        <h1>{scenario.name}</h1>
        <div className="minimal-eta">
          <strong>ETA 14분</strong>
          <span>자동 경로 안내 중</span>
        </div>
      </section>
      <section className="minimal-playback">
        <div className="live-pill">
          <span /> {STATUS[playback]}
        </div>
        <Equalizer active={playback === "playing"} />
        <h2>출발 전 브리핑을 음성으로 읽고 있습니다</h2>
        <p>자동 일방향 재생 · 질문이나 추가 응답 없이 핵심 내용만 안내</p>
        <div className="drive-actions three">
          <button
            className="pause-button"
            onClick={() => setPlayback((p) => (p === "playing" ? "paused" : "playing"))}
          >
            <Icon name={playback === "playing" ? "pause" : "play"} size={34} />
            {playback === "playing" ? "일시정지" : playback === "paused" ? "계속 재생" : "처음부터 재생"}
          </button>
          <button className="pause-button stop-button" onClick={() => setPlayback("stopped")} disabled={playback === "stopped"}>
            <Icon name="stop" size={34} />
            중지
          </button>
          <button className="voice-button" onClick={onVoiceMemo}>
            <Icon name="mic" size={34} />
            음성 메모 (1-Tap)
          </button>
        </div>
        <small className="gleo-caption">P1 확장: 핸들 음성 버튼(Gleo Interface) 호출 지원</small>
      </section>
    </main>
  );
}
