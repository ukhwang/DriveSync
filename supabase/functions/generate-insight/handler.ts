import { HttpError, json, parseBody, validated } from "../_shared/http.ts";
import { requireConsent, requireQuota, requireUser } from "../_shared/guards.ts";
import { LlmError } from "../_shared/llm.ts";
import { llmFailure } from "../_shared/llm-errors.ts";
import { INSIGHT_SYSTEM, insightUser } from "../_shared/prompts.ts";
import { Insight, InsightRequest } from "../_shared/schemas.ts";
import type { Deps } from "../_shared/ports.ts";

/** Meeting cheat sheet with per-line source (user vs AI). Only schema-valid output is stored. */
export function insightHandler(deps: Deps) {
  return async (req: Request): Promise<Response> => {
    const input = await parseBody(req, InsightRequest);
    const user = await requireUser(deps, req);
    await requireConsent(user);
    await requireQuota(user, "generate-insight");

    const ctx = await user.visitContext(input.visitId);
    if (!ctx) throw new HttpError("not_found", "visit not found");

    let insight;
    try {
      insight = await deps.llm.structured({
        system: INSIGHT_SYSTEM,
        user: insightUser(ctx, input.travelNotes),
        schema: Insight,
      });
    } catch (err) {
      if (err instanceof LlmError) throw llmFailure(err);
      throw err;
    }
    const checked = validated(Insight, insight);
    await user.saveInsight(input.visitId, checked);
    return json(checked);
  };
}
