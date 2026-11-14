import { HttpError } from "./http.ts";
import type { LlmError } from "./llm.ts";

export function llmFailure(err: LlmError): HttpError {
  switch (err.kind) {
    case "invalid_output":
      return new HttpError("schema_rejected", "AI output failed validation");
    case "refusal":
      return new HttpError("schema_rejected", "AI declined to produce this output");
    case "rate_limited":
      return new HttpError("rate_limited", "AI provider busy", 30);
    case "upstream":
      return new HttpError("upstream_failed", "AI provider unavailable");
  }
}
