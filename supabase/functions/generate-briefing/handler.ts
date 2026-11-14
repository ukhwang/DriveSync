import { HttpError, json, parseBody, validated } from "../_shared/http.ts";
import { requireConsent, requireQuota, requireUser } from "../_shared/guards.ts";
import { LlmError } from "../_shared/llm.ts";
import { BRIEFING_SYSTEM, briefingUser } from "../_shared/prompts.ts";
import { Briefing, BriefingRequest } from "../_shared/schemas.ts";
import type { Deps } from "../_shared/ports.ts";
import { llmFailure } from "../_shared/llm-errors.ts";

/** ETA-aware briefing (기획서 1-②). Tier is decided on the device from the live ETA. */
export function briefingHandler(deps: Deps) {
  return async (req: Request): Promise<Response> => {
    const input = await parseBody(req, BriefingRequest);
    const user = await requireUser(deps, req);
    await requireConsent(user);
    await requireQuota(user, "generate-briefing");

    const ctx = await user.visitContext(input.visitId);
    if (!ctx) throw new HttpError("not_found", "visit not found");

    try {
      const briefing = await deps.llm.structured({
        system: BRIEFING_SYSTEM,
        user: briefingUser(ctx, input.tier),
        schema: Briefing,
      });
      if (briefing.tier !== input.tier) throw new HttpError("schema_rejected", "tier mismatch");
      if (!briefing.voiceScript.some((s) => s.core)) throw new HttpError("schema_rejected", "no core sentence");
      if ((input.tier === "short") !== (briefing.preArrivalRecap === null)) {
        throw new HttpError("schema_rejected", "recap does not match tier");
      }
      return json(validated(Briefing, briefing));
    } catch (err) {
      if (err instanceof LlmError) throw llmFailure(err);
      throw err;
    }
  };
}
