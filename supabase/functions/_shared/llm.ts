import Anthropic from "@anthropic-ai/sdk";
import { betaZodOutputFormat } from "@anthropic-ai/sdk/helpers/beta/zod";
import type { z } from "zod";

export type LlmFailure = "refusal" | "invalid_output" | "upstream" | "rate_limited";

export class LlmError extends Error {
  constructor(readonly kind: LlmFailure, message: string) {
    super(message);
  }
}

export interface StructuredRequest<S extends z.ZodType> {
  system: string;
  user: string;
  schema: S;
}

/** Port: anything that turns a prompt into schema-valid JSON. Faked in tests. */
export interface Llm {
  structured<S extends z.ZodType>(req: StructuredRequest<S>): Promise<z.infer<S>>;
}

/**
 * Claude via the official SDK with structured outputs (Zod schema -> constrained JSON).
 * The API key exists only as an Edge Function secret (ANTHROPIC_API_KEY), never in the APK.
 */
export class AnthropicLlm implements Llm {
  private readonly client: Anthropic;

  constructor(
    apiKey: string,
    private readonly model = "claude-opus-5-5",
  ) {
    this.client = new Anthropic({ apiKey, maxRetries: 2, timeout: 60_000 });
  }

  async structured<S extends z.ZodType>(req: StructuredRequest<S>): Promise<z.infer<S>> {
    let message;
    try {
      message = await this.client.beta.messages.parse({
        model: this.model,
        max_tokens: 16000,
        system: req.system,
        messages: [{ role: "user", content: req.user }],
        // Short extraction/summarisation work: low effort keeps latency fit for a car stop.
        output_config: { effort: "low", format: betaZodOutputFormat(req.schema) },
        // On a policy refusal the API re-runs the request on a fallback model in the same call.
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
      });
    } catch (err) {
      if (err instanceof Anthropic.RateLimitError) throw new LlmError("rate_limited", "LLM rate limited");
      if (err instanceof Anthropic.APIError) throw new LlmError("upstream", `LLM API error ${err.status ?? ""}`.trim());
      throw err;
    }

    if (message.stop_reason === "refusal") throw new LlmError("refusal", "LLM declined the request");
    if (message.stop_reason === "max_tokens") throw new LlmError("invalid_output", "LLM output truncated");
    // Defence in depth: re-validate even though the output was schema-constrained.
    const checked = req.schema.safeParse(message.parsed_output);
    if (!checked.success) throw new LlmError("invalid_output", checked.error.message);
    return checked.data;
  }
}
