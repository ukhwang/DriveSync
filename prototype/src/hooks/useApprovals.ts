import { useEffect, useState } from "react";
import { emailDraft, type FollowUpId, type Scenario } from "../data/demo";

/** Draft -> Review -> Approved, per item (Human-in-the-Loop, proposal 2-③). */
export type ApprovalStatus = "draft" | "review" | "approved";

export interface EmailState {
  status: ApprovalStatus;
  version: number;
  subject: string;
  body: string;
}

export interface CalendarState {
  status: ApprovalStatus;
  version: number;
}

export interface Approvals {
  email: EmailState;
  calendar: CalendarState;
}

function initial(scenario: Scenario): Approvals {
  const draft = emailDraft(scenario);
  return {
    email: { status: "draft", version: 1, subject: draft.subject, body: draft.body },
    calendar: { status: "draft", version: 1 },
  };
}

export function useApprovals(scenario: Scenario) {
  const [state, setState] = useState<Approvals>(() => initial(scenario));

  useEffect(() => setState(initial(scenario)), [scenario]);

  const update = <K extends FollowUpId>(id: K, change: (current: Approvals[K]) => Approvals[K]) =>
    setState((current) => ({ ...current, [id]: change(current[id]) }));

  return {
    approvals: state,
    startReview: (id: FollowUpId) =>
      update(id, (item) => (item.status === "draft" ? { ...item, status: "review" } : item)),
    approve: (id: FollowUpId) =>
      update(id, (item) => (item.status === "review" ? { ...item, status: "approved" } : item)),
    /** Editing an approved draft sends it back to Review and bumps the version (new idempotency key). */
    editEmail: (patch: Partial<Pick<EmailState, "subject" | "body">>) =>
      update("email", (item) => ({
        ...item,
        ...patch,
        status: item.status === "draft" ? "draft" : "review",
        version: item.status === "approved" ? item.version + 1 : item.version,
      })),
    approvedIds: (Object.keys(state) as FollowUpId[]).filter((id) => state[id].status === "approved"),
  };
}
