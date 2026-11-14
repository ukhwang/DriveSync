import type { z } from "zod";

export type ErrorCode =
  | "method_not_allowed"
  | "invalid_input"
  | "unauthorized"
  | "consent_required"
  | "not_found"
  | "conflict"
  | "schema_rejected"
  | "recipient_not_allowed"
  | "rate_limited"
  | "upstream_failed"
  | "send_failed";

const STATUS: Record<ErrorCode, number> = {
  method_not_allowed: 405,
  invalid_input: 400,
  unauthorized: 401,
  consent_required: 403,
  not_found: 404,
  conflict: 409,
  schema_rejected: 422,
  recipient_not_allowed: 422,
  rate_limited: 429,
  upstream_failed: 502,
  send_failed: 502,
};

export class HttpError extends Error {
  constructor(readonly code: ErrorCode, message: string, readonly retryAfterSeconds?: number) {
    super(message);
  }
}

export function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

export function errorResponse(err: HttpError): Response {
  const headers: Record<string, string> = err.retryAfterSeconds ? { "retry-after": String(err.retryAfterSeconds) } : {};
  return json({ error: err.code, message: err.message }, STATUS[err.code], headers);
}

export async function parseBody<S extends z.ZodType>(req: Request, schema: S): Promise<z.infer<S>> {
  if (req.method !== "POST") throw new HttpError("method_not_allowed", "POST only");
  let raw: unknown;
  try {
    raw = await req.json();
  } catch {
    throw new HttpError("invalid_input", "body must be JSON");
  }
  const parsed = schema.safeParse(raw);
  if (!parsed.success) {
    throw new HttpError(
      "invalid_input",
      parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; "),
    );
  }
  return parsed.data;
}

/** Validates what we are about to return. A failure here is our bug or an LLM slip: 422. */
export function validated<S extends z.ZodType>(schema: S, value: unknown): z.infer<S> {
  const parsed = schema.safeParse(value);
  if (!parsed.success) throw new HttpError("schema_rejected", "response failed schema validation");
  return parsed.data;
}

export function serve(handler: (req: Request) => Promise<Response>): (req: Request) => Promise<Response> {
  return async (req) => {
    try {
      return await handler(req);
    } catch (err) {
      if (err instanceof HttpError) return errorResponse(err);
      console.error("unhandled", err instanceof Error ? err.message : err);
      return json({ error: "upstream_failed", message: "internal error" }, 500);
    }
  };
}
