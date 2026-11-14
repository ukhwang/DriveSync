import { HttpError, json, parseBody, validated } from "../_shared/http.ts";
import { requireConsent, requireQuota, requireUser } from "../_shared/guards.ts";
import { LlmError } from "../_shared/llm.ts";
import { llmFailure } from "../_shared/llm-errors.ts";
import { EXTRACT_SYSTEM, extractUser } from "../_shared/prompts.ts";
import { ExtractActionsRequest, ExtractActionsResponse, ExtractionOutput } from "../_shared/schemas.ts";
import type { Deps } from "../_shared/ports.ts";

/**
 * Debrief -> decisions + follow-up email DRAFTS. Drafts are stored with status "draft";
 * nothing is sent here (기획서 2-③ Human-in-the-Loop).
 */
export function extractActionsHandler(deps: Deps) {
  return async (req: Request): Promise<Response> => {
    const input = await parseBody(req, ExtractActionsRequest);
    const user = await requireUser(deps, req);
    await requireConsent(user);
    await requireQuota(user, "extract-actions");

    const ctx = await user.visitContext(input.visitId);
    if (!ctx) throw new HttpError("not_found", "visit not found");

    let output;
    try {
      output = await deps.llm.structured({
        system: EXTRACT_SYSTEM,
        user: extractUser(ctx, input.debrief),
        schema: ExtractionOutput,
      });
    } catch (err) {
      if (err instanceof LlmError) throw llmFailure(err);
      throw err;
    }

    const stored = await deps.service.insertDraftActions(user.userId, input.sessionId, ctx.customerId, output.emails);
    return json(validated(ExtractActionsResponse, {
      decisions: output.decisions,
      actions: stored.map((a) => ({ id: a.id, type: "email", draft: a.draft })),
    }));
  };
}
