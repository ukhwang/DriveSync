import { AnthropicLlm } from "./llm.ts";
import { ResendMailer } from "./resend.ts";
import { SupabaseServiceRepo, SupabaseUserRepo } from "./supabase.ts";
import type { Deps } from "./ports.ts";

function required(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`missing secret ${name}`);
  return value;
}

/** Production wiring. Secrets come from `supabase secrets set`, never from the client. */
export function productionDeps(): Deps {
  const url = required("SUPABASE_URL");
  const anonKey = required("SUPABASE_ANON_KEY");
  return {
    authenticate: (req) => SupabaseUserRepo.fromRequest(req, url, anonKey),
    service: new SupabaseServiceRepo(url, required("SUPABASE_SERVICE_ROLE_KEY")),
    llm: new AnthropicLlm(required("ANTHROPIC_API_KEY"), Deno.env.get("LLM_MODEL") ?? "claude-opus-5-5"),
    mailer: new ResendMailer(
      Deno.env.get("RESEND_API_KEY") ?? "",
      Deno.env.get("MAIL_FROM") ?? "DriveSync <onboarding@resend.dev>",
    ),
    allowedRecipientDomains: (Deno.env.get("ALLOWED_RECIPIENT_DOMAINS") ?? "")
      .split(",")
      .map((d) => d.trim().toLowerCase())
      .filter(Boolean),
  };
}
