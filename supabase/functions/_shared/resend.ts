import type { EmailDraftT } from "./schemas.ts";
import type { Mailer, SendResult } from "./ports.ts";

/**
 * Real email delivery through Resend. The Idempotency-Key header makes the provider itself
 * drop a duplicate send, on top of the action_executions table.
 */
export class ResendMailer implements Mailer {
  constructor(
    private readonly apiKey: string,
    private readonly from: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {}

  async send(draft: EmailDraftT, idempotencyKey: string): Promise<SendResult> {
    let response: Response;
    try {
      response = await this.fetchImpl("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          "authorization": `Bearer ${this.apiKey}`,
          "content-type": "application/json",
          "idempotency-key": idempotencyKey,
        },
        body: JSON.stringify({ from: this.from, to: [draft.to], subject: draft.subject, text: draft.body }),
        signal: AbortSignal.timeout(15_000),
      });
    } catch (err) {
      return { ok: false, reason: `mail provider unreachable: ${err instanceof Error ? err.name : "error"}` };
    }
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      return { ok: false, reason: `mail provider ${response.status}: ${String(body?.message ?? "error")}` };
    }
    return { ok: true, providerId: String(body?.id ?? "unknown") };
  }
}
