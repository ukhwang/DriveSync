import type { EmailDraftT, InsightT } from "./schemas.ts";
import type { Llm } from "./llm.ts";

export interface VisitContext {
  visitId: string;
  purpose: string;
  placeName: string;
  customerId: string;
  companyName: string;
  contactName: string;
  contactEmail: string;
  historySummary: string;
  /** Unfinished follow-ups carried over from earlier visits. */
  openActions: { subject: string; status: string }[];
}

export interface ActionRow {
  id: string;
  ownerId: string;
  customerId: string;
  status: string;
}

export type ClaimResult = "claimed" | "already_executed" | "in_progress";

/** Data access as the signed-in user (RLS applies). */
export interface UserRepo {
  readonly userId: string;
  hasConsent(): Promise<boolean>;
  setConsent(granted: boolean): Promise<void>;
  consumeQuota(fn: string, limit: number, windowSeconds: number): Promise<boolean>;
  visitContext(visitId: string): Promise<VisitContext | null>;
  saveInsight(visitId: string, insight: InsightT): Promise<void>;
  getAction(actionId: string): Promise<ActionRow | null>;
}

/** Privileged writes (service role). Only reachable after the user-level checks pass. */
export interface ServiceRepo {
  insertDraftActions(
    ownerId: string,
    sessionId: string,
    customerId: string,
    drafts: EmailDraftT[],
  ): Promise<{ id: string; draft: EmailDraftT }[]>;
  claimExecution(ownerId: string, actionId: string, idempotencyKey: string): Promise<ClaimResult>;
  markApproved(actionId: string, draft: EmailDraftT, draftVersion: number, idempotencyKey: string): Promise<void>;
  finishExecution(
    actionId: string,
    idempotencyKey: string,
    outcome: { ok: true; providerId: string } | { ok: false; reason: string },
  ): Promise<void>;
}

export type SendResult = { ok: true; providerId: string } | { ok: false; reason: string };

export interface Mailer {
  send(draft: EmailDraftT, idempotencyKey: string): Promise<SendResult>;
}

export interface Deps {
  /** Returns null when the bearer token is missing or invalid. */
  authenticate(req: Request): Promise<UserRepo | null>;
  service: ServiceRepo;
  llm: Llm;
  mailer: Mailer;
  /** Comma-separated domains; empty = any recipient. Protects live demos from mis-sends. */
  allowedRecipientDomains: string[];
}
