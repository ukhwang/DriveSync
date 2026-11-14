import { HttpError, json, parseBody, validated } from "../_shared/http.ts";
import { requireQuota, requireUser } from "../_shared/guards.ts";
import { ExecuteActionRequest, ExecuteActionResponse } from "../_shared/schemas.ts";
import type { Deps } from "../_shared/ports.ts";

/**
 * Executes ONE user-approved action (real email send).
 *
 * - The request itself is the approval signal; it is accepted only for the caller's own action.
 * - action_executions has the idempotency key as primary key: a key is sent at most once.
 * - The response says "executed" only after the mail provider confirmed. Failures return 502
 *   and the action is stored as failed with its reason (기획서: 실패를 성공으로 표시하지 않습니다).
 */
export function executeActionHandler(deps: Deps) {
  return async (req: Request): Promise<Response> => {
    const input = await parseBody(req, ExecuteActionRequest);
    const user = await requireUser(deps, req);
    await requireQuota(user, "execute-action");

    if (input.idempotencyKey !== `${input.actionId}:v${input.draftVersion}`) {
      throw new HttpError("invalid_input", "idempotency key does not match action and draft version");
    }
    const action = await user.getAction(input.actionId);
    if (!action || action.ownerId !== user.userId) throw new HttpError("not_found", "action not found");
    if (action.status === "executed") {
      return json(validated(ExecuteActionResponse, { status: "already_executed", log: "action already executed" }));
    }

    const domain = input.draft.to.split("@")[1]?.toLowerCase() ?? "";
    if (deps.allowedRecipientDomains.length > 0 && !deps.allowedRecipientDomains.includes(domain)) {
      throw new HttpError("recipient_not_allowed", `recipient domain ${domain} is not allowed in this environment`);
    }

    const claim = await deps.service.claimExecution(user.userId, input.actionId, input.idempotencyKey);
    if (claim === "already_executed") {
      return json(
        validated(ExecuteActionResponse, {
          status: "already_executed",
          log: `key ${input.idempotencyKey} already sent`,
        }),
      );
    }
    if (claim === "in_progress") throw new HttpError("conflict", "execution already in progress");

    await deps.service.markApproved(input.actionId, input.draft, input.draftVersion, input.idempotencyKey);
    const result = await deps.mailer.send(input.draft, input.idempotencyKey);
    await deps.service.finishExecution(input.actionId, input.idempotencyKey, result);

    if (!result.ok) throw new HttpError("send_failed", result.reason);
    return json(validated(ExecuteActionResponse, { status: "executed", log: `sent ${result.providerId}` }));
  };
}
