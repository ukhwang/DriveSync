import { useState } from "react";
import { CALENDAR_ITEM, DEBRIEF, type FollowUpId, type Scenario } from "../data/demo";
import type { ApprovalStatus, Approvals } from "../hooks/useApprovals";
import { SourceBadge } from "../components/Badges";
import { Icon } from "../components/Icon";

const STATUS_LABEL: Record<ApprovalStatus, string> = {
  draft: "Draft · 초안",
  review: "Review · 검토 중",
  approved: "Approved · 승인됨",
};

function StatusPill({ status }: { status: ApprovalStatus }) {
  return <span className={`approval-pill ${status}`}>{STATUS_LABEL[status]}</span>;
}

function ItemAction({
  status,
  onReview,
  onApprove,
}: {
  status: ApprovalStatus;
  onReview: () => void;
  onApprove: () => void;
}) {
  if (status === "draft") {
    return (
      <button className="item-action" onClick={onReview}>
        <Icon name="edit" size={20} /> 검토 시작
      </button>
    );
  }
  if (status === "review") {
    return (
      <button className="item-action primary" onClick={onApprove}>
        <Icon name="check" size={20} /> 이 항목 승인
      </button>
    );
  }
  return (
    <span className="item-action done">
      <Icon name="check" size={20} /> 승인 완료
    </span>
  );
}

/**
 * 05 미팅 후 디브리핑 + 개별 승인 (Human-in-the-Loop).
 * Each follow-up (email, calendar) goes Draft -> Review -> Approved on its own.
 * Only approved items are executed; editing an approved email returns it to Review.
 */
export function State05Approval({
  scenario,
  approvals,
  onReview,
  onApprove,
  onEditEmail,
  approvedCount,
  onExecute,
}: {
  scenario: Scenario;
  approvals: Approvals;
  onReview: (id: FollowUpId) => void;
  onApprove: (id: FollowUpId) => void;
  onEditEmail: (patch: { subject?: string; body?: string }) => void;
  approvedCount: number;
  onExecute: () => void;
}) {
  const [debrief, setDebrief] = useState<string>(DEBRIEF.text);
  const [addingDebrief, setAddingDebrief] = useState(false);
  const { email, calendar } = approvals;
  const emailEditable = email.status !== "draft";

  return (
    <main className="parked-approval-workspace">
      <section className="debrief-input-panel">
        <div className="workspace-panel-heading">
          <span>POST-MEETING INPUT</span>
          <h1>미팅 후 디브리핑 입력</h1>
          <p>STT / 텍스트 · 사용자 원본은 기기에 먼저 저장</p>
        </div>

        <div className="debrief-note-card">
          <div className="debrief-note-meta">
            <span>
              <Icon name="mic" size={21} />
              미팅 직후 음성 기록
            </span>
            <SourceBadge source="user" />
            <small>
              {DEBRIEF.recordedAt} · {DEBRIEF.duration}
            </small>
          </div>
          {addingDebrief ? (
            <textarea
              value={debrief}
              onChange={(event) => setDebrief(event.target.value)}
              autoFocus
              aria-label="미팅 후 디브리핑 수정"
            />
          ) : (
            <p>“{debrief}”</p>
          )}
          <button onClick={() => setAddingDebrief((value) => !value)}>
            <Icon name={addingDebrief ? "check" : "mic"} size={22} />
            {addingDebrief ? "추가 입력 반영" : "디브리핑 추가 입력"}
          </button>
        </div>

        <div className="integration-flow">
          <div>
            <Icon name="database" size={22} />
            <span>주행 메모 + 디브리핑 통합 → AI 초안 생성</span>
          </div>
          <i />
          <span className="flow-arrow">
            <Icon name="chevron" size={28} />
          </span>
        </div>

        <div className="source-summary">
          <span>
            <Icon name="check" size={18} />
            주행 중 음성 메모 1건
          </span>
          <span>
            <Icon name="check" size={18} />
            미팅 직후 디브리핑 1건
          </span>
        </div>
      </section>

      <section className="final-approval-panel">
        <div className="workspace-panel-heading horizontal">
          <div>
            <span>HUMAN-IN-THE-LOOP</span>
            <h1>후속 작업 개별 승인</h1>
          </div>
          <span className="review-state-pill">승인 {approvedCount} / 2</span>
        </div>

        <div className="final-email-draft">
          <div className="draft-title-row">
            <div>
              <Icon name="mail" size={27} />
              <span>
                <small>EMAIL DRAFT</small>
                <strong>감사 메일 및 수정 견적서 발송</strong>
              </span>
            </div>
            <div className="item-meta">
              <SourceBadge source="ai" />
              <StatusPill status={email.status} />
            </div>
          </div>
          <div className="final-email-fields">
            <label>
              수신<strong>{scenario.email}</strong>
            </label>
            <label>
              제목
              <input
                value={email.subject}
                readOnly={!emailEditable}
                onChange={(event) => onEditEmail({ subject: event.target.value })}
              />
            </label>
          </div>
          <textarea
            value={email.body}
            readOnly={!emailEditable}
            onChange={(event) => onEditEmail({ body: event.target.value })}
            aria-label="후속 이메일 본문"
          />
          <div className="item-footer">
            <span>v{email.version} · 수정하면 다시 검토 상태로 돌아갑니다</span>
            <ItemAction status={email.status} onReview={() => onReview("email")} onApprove={() => onApprove("email")} />
          </div>
        </div>

        <div className={`calendar-approval-row ${calendar.status === "approved" ? "checked" : ""}`}>
          <span>
            <Icon name="check" size={22} />
          </span>
          <Icon name="calendar" size={28} />
          <div>
            <small>NEXT ACTION · 일정</small>
            <strong>{CALENDAR_ITEM.title}</strong>
          </div>
          <div className="item-meta">
            <SourceBadge source="ai" />
            <StatusPill status={calendar.status} />
            <ItemAction
              status={calendar.status}
              onReview={() => onReview("calendar")}
              onApprove={() => onApprove("calendar")}
            />
          </div>
        </div>

        <div className="final-approval-footer">
          <p>
            <Icon name="shield" size={21} />
            승인된 항목만 외부 시스템에 반영됩니다.
          </p>
          <button onClick={onExecute} disabled={approvedCount === 0}>
            <Icon name="check" size={26} />
            {approvedCount === 0 ? "승인된 항목 없음" : `승인된 ${approvedCount}건 실행`}
          </button>
        </div>
      </section>
    </main>
  );
}
