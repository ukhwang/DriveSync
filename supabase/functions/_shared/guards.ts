import { HttpError } from "./http.ts";
import type { Deps, UserRepo } from "./ports.ts";

export const QUOTAS = {
  "generate-briefing": { limit: 30, windowSeconds: 3600 },
  "generate-insight": { limit: 30, windowSeconds: 3600 },
  "extract-actions": { limit: 20, windowSeconds: 3600 },
  "execute-action": { limit: 30, windowSeconds: 3600 },
} as const;

export async function requireUser(deps: Deps, req: Request): Promise<UserRepo> {
  const user = await deps.authenticate(req);
  if (!user) throw new HttpError("unauthorized", "sign in required");
  return user;
}

/** 기획서 3-⑥: customer data goes to the external LLM only after explicit consent. */
export async function requireConsent(user: UserRepo): Promise<void> {
  if (!(await user.hasConsent())) throw new HttpError("consent_required", "LLM data-sharing consent required");
}

export async function requireQuota(user: UserRepo, fn: keyof typeof QUOTAS): Promise<void> {
  const { limit, windowSeconds } = QUOTAS[fn];
  if (!(await user.consumeQuota(fn, limit, windowSeconds))) {
    throw new HttpError("rate_limited", "too many requests", 60);
  }
}
