import { json, parseBody } from "../_shared/http.ts";
import { requireUser } from "../_shared/guards.ts";
import { ConsentRequest } from "../_shared/schemas.ts";
import type { Deps } from "../_shared/ports.ts";

/** Records (or withdraws) consent to send customer data to the external LLM. */
export function consentHandler(deps: Deps) {
  return async (req: Request): Promise<Response> => {
    const input = await parseBody(req, ConsentRequest);
    const user = await requireUser(deps, req);
    await user.setConsent(input.granted);
    return json(input.granted);
  };
}
