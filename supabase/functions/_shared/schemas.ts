/**
 * Wire contracts — the SINGLE SOURCE OF TRUTH.
 *
 * - Edge Functions validate every input and every LLM output with these schemas.
 * - `deno task schemas` regenerates /schemas/*.schema.json from them (CI fails on drift),
 *   and the Kotlin client mirrors them in core/ai (kotlinx.serialization + ContractValidator).
 */
import { z } from "zod";

// ---------------------------------------------------------------- shared
export const ItemSource = z.enum(["user", "ai"]);

export const SourcedText = z.object({
  text: z.string().trim().min(1).max(200),
  source: ItemSource.describe("user = the sales rep said it; ai = inferred, must be confirmed"),
});

export const BriefingTier = z.enum(["short", "medium", "long"]);

const Id = z.string().trim().min(1).max(100);

// ---------------------------------------------------------------- briefing
export const BriefingRequest = z.object({
  visitId: Id,
  etaMinutes: z.number().int().min(0).max(600),
  tier: BriefingTier,
});

export const BriefingSentence = z.object({
  text: z.string().trim().min(1).max(200),
  core: z.boolean().describe("core sentences survive when the remaining ETA is too short"),
});

export const Briefing = z.object({
  tier: BriefingTier,
  screenLines: z.array(z.string().trim().min(1).max(120)).min(1).max(8),
  voiceScript: z.array(BriefingSentence).min(1).max(20),
  preArrivalRecap: z.string().trim().min(1).max(120).nullable(),
});

// ---------------------------------------------------------------- insight (cheat sheet)
export const InsightRequest = z.object({
  visitId: Id,
  travelNotes: z.array(z.string().trim().min(1).max(1000)).max(30),
});

export const Insight = z.object({
  summary: z.array(SourcedText).length(3),
  concerns: z.array(SourcedText).max(5),
  expectedQuestions: z.array(SourcedText).max(5),
});

// ---------------------------------------------------------------- debrief -> actions
export const EmailDraft = z.object({
  to: z.email(),
  subject: z.string().trim().min(1).max(150),
  body: z.string().trim().min(1).max(4000),
});

export const ExtractActionsRequest = z.object({
  sessionId: z.uuid(),
  visitId: Id,
  debrief: z.string().trim().min(1).max(5000),
});

/** What the LLM must return (no ids; the server assigns them). */
export const ExtractionOutput = z.object({
  decisions: z.array(z.string().trim().min(1).max(200)).max(10),
  emails: z.array(EmailDraft).max(5),
});

export const ExtractedAction = z.object({
  id: Id,
  type: z.literal("email"),
  draft: EmailDraft,
});

export const ExtractActionsResponse = z.object({
  decisions: z.array(z.string()),
  actions: z.array(ExtractedAction),
});

// ---------------------------------------------------------------- execution
export const ExecuteActionRequest = z.object({
  actionId: Id,
  idempotencyKey: z.string().regex(/^[^:\s]+:v\d+$/, "expected <actionId>:v<draftVersion>"),
  draft: EmailDraft,
  draftVersion: z.number().int().min(1),
});

export const ExecuteActionResponse = z.object({
  status: z.enum(["executed", "already_executed"]),
  log: z.string(),
});

export const ConsentRequest = z.object({ granted: z.boolean() });

export type BriefingT = z.infer<typeof Briefing>;
export type InsightT = z.infer<typeof Insight>;
export type ExtractionOutputT = z.infer<typeof ExtractionOutput>;
export type EmailDraftT = z.infer<typeof EmailDraft>;

/** Exported for the JSON Schema generator. */
export const CONTRACTS = {
  "briefing-request": BriefingRequest,
  "briefing": Briefing,
  "insight-request": InsightRequest,
  "insight": Insight,
  "extract-actions-request": ExtractActionsRequest,
  "extract-actions-response": ExtractActionsResponse,
  "execute-action-request": ExecuteActionRequest,
  "execute-action-response": ExecuteActionResponse,
} as const;
