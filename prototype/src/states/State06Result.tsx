import { CALENDAR_ITEM, OFFLINE_CONTEXT, idempotencyKey, type FollowUpId, type Scenario } from "../data/demo";
import type { Approvals } from "../hooks/useApprovals";
import type { RetryPhase } from "../hooks/useRetryFlow";
import { Icon } from "../components/Icon";

const BADGE: Record<RetryPhase, string> = {
  failed: "Failed · 오프라인 로컬 큐 보관",
  retry: "Retry · 다시 승인됨 · 연결 대기",
  executed: "Executed · 재시도 성공",
};

/**
 * 06 실행 결과 + 오프라인 재시도.
 * Demo story: the email was sent before entering an underground car park; the calendar sync
 * failed there. Failed -> re-approve -> Retry -> connection back -> Executed, same idempotency key.
 * Only items approved in state 05 appear here.
 */
export function State06Result({
  scenario,
  approvals,
  approvedIds,
  phase,
  onReapprove,
  onReconnect,
  onBackToApproval,
}: {
  scenario: Scenario;
  approvals: Approvals;
  approvedIds: FollowUpId[];
  phase: RetryPhase;
  onReapprove: () => void;
  onReconnect: () => void;
  onBackToApproval: () => void;
}) {
  if (approvedIds.length === 0) {
    return (
      <main className="result-layout">
        <div className="result-heading">
          <span className="eyebrow">EXECUTION SUMMARY</span>
          <h1>승인된 작업이 없습니다</h1>
          <p>승인하지 않은 작업은 실행되지 않습니다. 05 화면에서 항목별로 승인해 주세요.</p>
        </div>
        <button className="empty-cta" onClick={onBackToApproval}>
          <Icon name="chevron" size={22} /> 05 디브리핑 · 승인으로 이동
        </button>
      </main>
    );
  }

  const failing: FollowUpId | null = approvedIds.includes("calendar") ? "calendar" : null;
  const succeeded = approvedIds.filter((id) => id !== failing);
  const failingKey = failing ? idempotencyKey(scenario, failing, approvals[failing].version) : "";

  return (
    <main className="result-layout">
      <div className="result-heading">
        <span className="eyebrow">EXECUTION SUMMARY</span>
        <h1>후속 작업 실행 결과</h1>
        <p>성공·실패 표시는 서버 응답만 근거로 합니다. 승인된 {approvedIds.length}건만 실행했습니다.</p>
      </div>
      <div className="result-cards">
        <section className="result-card success">
          <div className="result-icon">
            <Icon name="check" size={40} />
          </div>
          <span className="result-badge">Executed · 외부 반영 및 암호화 저장 완료</span>
          <h2>{succeeded.length > 0 ? "정상적으로 실행되었습니다" : "아직 완료된 작업이 없습니다"}</h2>
          <div className="log-list">
            {succeeded.includes("email") && (
              <div>
                <Icon name="mail" size={24} />
                <span>
                  <strong>감사 메일 및 견적서 발송 완료</strong>
                  <small>{scenario.email} · 서버 응답 200</small>
                </span>
                <Icon name="check" size={22} />
              </div>
            )}
            {succeeded.includes("email") && (
              <div>
                <Icon name="shield" size={24} />
                <span>
                  <strong>실행 키 검증 · 중복 발송 차단</strong>
                  <small>{idempotencyKey(scenario, "email", approvals.email.version)}</small>
                </span>
                <Icon name="check" size={22} />
              </div>
            )}
            {phase === "executed" && failing && (
              <div>
                <Icon name="calendar" size={24} />
                <span>
                  <strong>{CALENDAR_ITEM.title} 캘린더 등록 완료 (재시도)</strong>
                  <small>{failingKey} · 같은 실행 키로 1회만 반영</small>
                </span>
                <Icon name="check" size={22} />
              </div>
            )}
            <div>
              <Icon name="database" size={24} />
              <span>
                <strong>
                  {scenario.visitNo}회차 방문 이력 업데이트
                </strong>
                <small>Visit History · SQLCipher 암호화 저장</small>
              </span>
              <Icon name="check" size={22} />
            </div>
          </div>
        </section>

        {failing && (
          <section className={`result-card failure ${phase !== "failed" ? "retrying" : ""} ${phase === "executed" ? "recovered" : ""}`}>
            <div className="result-icon">
              <Icon name={phase === "failed" ? "wifi" : phase === "retry" ? "retry" : "check"} size={40} />
            </div>
            <span className="result-badge">{BADGE[phase]}</span>
            <h2>
              {phase === "failed"
                ? "캘린더 등록 실패 · 네트워크 없음"
                : phase === "retry"
                  ? "연결되면 같은 실행 키로 재시도합니다"
                  : "재시도로 반영되었습니다"}
            </h2>
            <p>
              {OFFLINE_CONTEXT} 작업은 SQLCipher 로컬 저장소에 암호화 보관 중이며, 실패를 성공으로 표시하지 않습니다.
            </p>
            <div className="queue-meta">
              <span>IDEMPOTENCY KEY</span>
              <strong>{failingKey}</strong>
              <small>AES-256 · SQLCipher</small>
            </div>
            {phase === "failed" && (
              <button onClick={onReapprove}>
                <Icon name="retry" size={24} />
                다시 승인 후 재시도
              </button>
            )}
            {phase === "retry" && (
              <button onClick={onReconnect}>
                <Icon name="wifi" size={24} />
                연결 복구 (시뮬레이션)
              </button>
            )}
          </section>
        )}
      </div>
    </main>
  );
}
