import { useState } from "react";
import { CHEAT_CARDS, type CheatCard, type Scenario, type SourcedLine } from "../data/demo";
import { SourceBadge } from "../components/Badges";
import { Icon } from "../components/Icon";

/** Edits stay local to the demo session; an edited AI line becomes the user's own record. */
function useCheatSheet() {
  const [cards, setCards] = useState<CheatCard[]>(() => CHEAT_CARDS.map((c) => ({ ...c, lines: [...c.lines] })));
  const saveLines = (index: number, lines: SourcedLine[]) =>
    setCards((current) => current.map((card, i) => (i === index ? { ...card, lines } : card)));
  return { cards, saveLines };
}

function counts(lines: SourcedLine[]) {
  return {
    user: lines.filter((l) => l.source === "user").length,
    ai: lines.filter((l) => l.source === "ai").length,
  };
}

/**
 * 04 도착 후 미팅 치트시트 — 3D cylinder carousel (Figma design), proposal content:
 * 핵심 3줄 / 우려사항 / 예상 질문 / 이동 중 메모. Tap the front card for details and editing.
 * No approval or sending here: follow-ups are approved only after the meeting (state 05).
 */
export function State04CheatSheet({ scenario }: { scenario: Scenario }) {
  const { cards, saveLines } = useCheatSheet();
  const [activeCard, setActiveCard] = useState(0);
  const [detailOpen, setDetailOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [draftLines, setDraftLines] = useState<SourcedLine[]>([]);
  const total = cards.length;
  const active = cards[activeCard] ?? cards[0]!;

  const rotateTo = (index: number) => {
    setActiveCard(index);
    setEditing(false);
  };
  const openDetail = (index: number) => {
    if (index !== activeCard) return rotateTo(index);
    setDetailOpen(true);
  };
  const startEdit = () => {
    setDraftLines(active.lines);
    setEditing(true);
  };
  const finishEdit = () => {
    saveLines(
      activeCard,
      draftLines.map((line, i) =>
        line.text !== active.lines[i]?.text ? { text: line.text, source: "user" } : line,
      ),
    );
    setEditing(false);
  };
  const close = () => {
    setDetailOpen(false);
    setEditing(false);
  };

  return (
    <main className={`orbital-screen ${detailOpen ? "detail-is-open" : ""}`}>
      <p className="orbital-instruction">
        주차(P) 전환 완료 · 카드를 선택해 회전시키고, 중앙 카드를 탭해 상세 내용을 확인·수정하세요.
      </p>

      <section className="orbital-stage cylinder-stage" aria-label="미팅 치트시트 3D 원통 캐러셀">
        <div className="cylinder-context">
          <span>
            {scenario.name} · {scenario.visitNo}회차 방문
          </span>
          <small>
            <Icon name="check" size={17} />
            미팅 전 확인 항목 {total}종
          </small>
        </div>
        <div className="cylinder-floor" />

        <div className="cylinder-ring">
          {cards.map((card, index) => {
            let offset = index - activeCard;
            if (offset > 2) offset -= total;
            if (offset < -2) offset += total;
            const isFront = offset === 0;
            const depthClass = isFront ? "front" : Math.abs(offset) === 1 ? "adjacent" : "rear";
            const c = counts(card.lines);
            return (
              <div
                className={`orbital-card-position ${depthClass} ${offset < 0 ? "left" : "right"}`}
                style={{ transform: `rotateY(${offset * 46}deg) translateZ(680px)` }}
                key={card.id}
              >
                <button
                  className={`orbital-card ${card.tone} ${isFront ? "active" : ""}`}
                  onClick={() => openDetail(index)}
                  aria-pressed={isFront}
                >
                  <i />
                  <span className="orbital-card-badge">{card.badge}</span>
                  <strong>{card.title}</strong>
                  <span className="card-source-counts">
                    {c.user > 0 && <span className="source-badge user">내 기록 {c.user}</span>}
                    {c.ai > 0 && <span className="source-badge ai">AI 추론 {c.ai}</span>}
                  </span>
                  <small>탭하여 상세 보기 ↗</small>
                </button>
              </div>
            );
          })}
        </div>

        <div className="orbital-controls">
          <button onClick={() => rotateTo((activeCard - 1 + total) % total)} aria-label="이전 카드로 회전">
            <Icon name="chevron" size={28} />
          </button>
          <span>
            {String(activeCard + 1).padStart(2, "0")} / {String(total).padStart(2, "0")}
          </span>
          <button onClick={() => rotateTo((activeCard + 1) % total)} aria-label="다음 카드로 회전">
            <Icon name="chevron" size={28} />
          </button>
        </div>
      </section>

      {detailOpen && (
        <div className="orbital-modal-overlay" onMouseDown={close}>
          <section
            className={`orbital-detail-modal ${active.tone}`}
            onMouseDown={(event) => event.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="orbital-detail-title"
          >
            <div className="modal-accent" />
            <header className="orbital-modal-header">
              <div>
                <span>{active.badge}</span>
                <h2 id="orbital-detail-title">{active.title}</h2>
              </div>
              <button className="modal-close" onClick={close} aria-label="상세 보기 닫기">
                × 닫기
              </button>
            </header>
            <div className="orbital-detail-body">
              {(editing ? draftLines : active.lines).map((line, index) => (
                <div className={`detail-section ${line.source === "ai" ? "ai" : ""}`} key={index}>
                  <SourceBadge source={line.source} />
                  {editing ? (
                    <textarea
                      value={line.text}
                      aria-label={`${active.badge} ${index + 1}번 항목 수정`}
                      onChange={(event) =>
                        setDraftLines((current) =>
                          current.map((l, i) => (i === index ? { ...l, text: event.target.value } : l)),
                        )
                      }
                    />
                  ) : (
                    <p>{active.id === "memos" ? `“${line.text}”` : line.text}</p>
                  )}
                </div>
              ))}
              <div className="detail-actions">
                <button className="detail-edit" onClick={editing ? finishEdit : startEdit}>
                  <Icon name={editing ? "check" : "edit"} size={21} />
                  {editing ? "수정 완료 (수정한 항목은 [내 기록]으로 표시)" : "내용 수정"}
                </button>
              </div>
            </div>
          </section>
        </div>
      )}
    </main>
  );
}
