import { useState } from "react";
import { BRIEF_CARDS, briefingPlan, type Scenario } from "../data/demo";
import { SourceBadge } from "../components/Badges";
import { Icon } from "../components/Icon";

/** 01 출발 전 브리핑 — ETA auto-match decides the briefing length. */
export function State01Briefing({ scenario, onReady }: { scenario: Scenario; onReady: () => void }) {
  const [previewing, setPreviewing] = useState(false);
  const plan = briefingPlan(scenario.etaMinutes);

  return (
    <main className="content-grid state-one-grid">
      <section className="driver-column state-one-left">
        <div className="visit-heading">
          <span>NEXT VISIT · {scenario.visitNo}회차 방문</span>
          <h1>{scenario.name}</h1>
          <p>{scenario.contact}</p>
        </div>

        <div className="route-eta-widget">
          <div className="eta-overview">
            <strong>{scenario.etaMinutes}분</strong>
            <div>
              <span>
                {scenario.arrival} 도착 예정 · {scenario.distanceKm} km
              </span>
              <b>{plan.label}</b>
            </div>
          </div>

          <div className="route-progress" aria-label={`현위치에서 ${scenario.name}까지 이동 경로`}>
            <div className="route-line">
              <span className="start-dot" />
              <i />
              <span className="destination-pin">
                <Icon name="route" size={18} />
              </span>
            </div>
            <div className="route-labels">
              <span>현위치</span>
              <strong>{scenario.name}</strong>
            </div>
          </div>
        </div>

        <div className="state-one-actions">
          <button className="brief-ready-button" onClick={onReady}>
            <Icon name="check" size={24} />
            주행 브리핑 준비 완료
          </button>
          <button
            className={`tts-preview-button ${previewing ? "playing" : ""}`}
            onClick={() => setPreviewing((current) => !current)}
            aria-pressed={previewing}
          >
            <Icon name={previewing ? "stop" : "play"} size={22} />
            {previewing ? "미리듣기 중지" : "음성 미리듣기 (TTS)"}
          </button>
        </div>
      </section>

      <section className="review-column state-one-right">
        <div className="state-one-section-header">
          <h2>출발 전 핵심 브리핑</h2>
          <span>
            주행 시작(D단) 시 약 {plan.seconds}초 음성으로 자동 안내{plan.recap ? " · 도착 5분 전 15초 재요약" : ""}
          </span>
        </div>

        <div className="modular-briefs">
          {BRIEF_CARDS.map((card, index) => (
            <article className={`modular-brief-card ${card.tone}`} key={card.title}>
              <div className="brief-card-copy">
                <div className="brief-badges">
                  <span className={`brief-context-badge ${card.labelTone}`}>{card.label}</span>
                  <SourceBadge source={card.source} />
                </div>
                <h3>{card.title}</h3>
                <p>{card.detail}</p>
              </div>
              <span className="brief-card-index">{String(index + 1).padStart(2, "0")}</span>
            </article>
          ))}
        </div>

        <div className="state-one-footer">
          <Icon name="shield" size={19} />
          <span>방문 이력·사용자 메모 기반 · [AI 추론] 항목은 현장에서 최종 확인</span>
        </div>
      </section>
    </main>
  );
}
