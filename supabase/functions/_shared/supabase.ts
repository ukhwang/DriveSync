import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import type { EmailDraftT, InsightT } from "./schemas.ts";
import type { ActionRow, ClaimResult, ServiceRepo, UserRepo, VisitContext } from "./ports.ts";

/** User-scoped repo: every query runs with the caller's JWT, so RLS enforces ownership. */
export class SupabaseUserRepo implements UserRepo {
  private constructor(readonly userId: string, private readonly db: SupabaseClient) {}

  static async fromRequest(req: Request, url: string, anonKey: string): Promise<SupabaseUserRepo | null> {
    const header = req.headers.get("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : "";
    if (!token) return null;
    const db = createClient(url, anonKey, {
      global: { headers: { Authorization: `Bearer ${token}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data, error } = await db.auth.getUser(token);
    if (error || !data.user) return null;
    return new SupabaseUserRepo(data.user.id, db);
  }

  async hasConsent(): Promise<boolean> {
    const { data } = await this.db.from("profiles").select("llm_consent_at").eq("id", this.userId).maybeSingle();
    return Boolean(data?.llm_consent_at);
  }

  async setConsent(granted: boolean): Promise<void> {
    const { error } = await this.db.from("profiles").upsert({
      id: this.userId,
      llm_consent_at: granted ? new Date().toISOString() : null,
    });
    if (error) throw new Error(`consent update failed: ${error.message}`);
  }

  async consumeQuota(fn: string, limit: number, windowSeconds: number): Promise<boolean> {
    const { data, error } = await this.db.rpc("consume_ai_quota", {
      p_function: fn,
      p_limit: limit,
      p_window_seconds: windowSeconds,
    });
    if (error) throw new Error(`quota check failed: ${error.message}`);
    return data === true;
  }

  async visitContext(visitId: string): Promise<VisitContext | null> {
    const { data: visit } = await this.db
      .from("visits")
      .select(
        "id, purpose, place_name, customer_id, customers(company_name, contact_name, contact_email, history_summary)",
      )
      .eq("id", visitId)
      .maybeSingle();
    if (!visit) return null;
    const customer = (Array.isArray(visit.customers) ? visit.customers[0] : visit.customers) as
      | { company_name: string; contact_name: string; contact_email: string; history_summary: string }
      | null;
    if (!customer) return null;
    const { data: open } = await this.db
      .from("actions")
      .select("draft, status")
      .eq("customer_id", visit.customer_id)
      .neq("status", "executed");
    return {
      visitId: visit.id,
      purpose: visit.purpose,
      placeName: visit.place_name,
      customerId: visit.customer_id,
      companyName: customer.company_name,
      contactName: customer.contact_name,
      contactEmail: customer.contact_email,
      historySummary: customer.history_summary,
      openActions: (open ?? []).map((row) => ({
        subject: String((row.draft as { subject?: string })?.subject ?? ""),
        status: String(row.status),
      })),
    };
  }

  async saveInsight(visitId: string, insight: InsightT): Promise<void> {
    const { error } = await this.db.from("insights").upsert({
      visit_id: visitId,
      owner_id: this.userId,
      payload: insight,
    });
    if (error) throw new Error(`insight save failed: ${error.message}`);
  }

  async getAction(actionId: string): Promise<ActionRow | null> {
    const { data } = await this.db.from("actions").select("id, owner_id, customer_id, status").eq("id", actionId)
      .maybeSingle();
    return data ? { id: data.id, ownerId: data.owner_id, customerId: data.customer_id, status: data.status } : null;
  }
}

/** Service-role repo for writes clients may not perform directly (action status). */
export class SupabaseServiceRepo implements ServiceRepo {
  private readonly db: SupabaseClient;

  constructor(url: string, serviceRoleKey: string) {
    this.db = createClient(url, serviceRoleKey, { auth: { persistSession: false, autoRefreshToken: false } });
  }

  async insertDraftActions(ownerId: string, sessionId: string, customerId: string, drafts: EmailDraftT[]) {
    const rows = drafts.map((draft) => ({
      id: crypto.randomUUID(),
      owner_id: ownerId,
      session_id: sessionId,
      customer_id: customerId,
      type: "email",
      draft,
      status: "draft",
    }));
    if (rows.length === 0) return [];
    const { error } = await this.db.from("actions").insert(rows);
    if (error) throw new Error(`action insert failed: ${error.message}`);
    return rows.map((r) => ({ id: r.id, draft: r.draft }));
  }

  async claimExecution(ownerId: string, actionId: string, idempotencyKey: string): Promise<ClaimResult> {
    const { error } = await this.db.from("action_executions").insert({
      idempotency_key: idempotencyKey,
      action_id: actionId,
      owner_id: ownerId,
      status: "pending",
    });
    if (!error) return "claimed";
    if (error.code !== "23505") throw new Error(`claim failed: ${error.message}`);

    const { data } = await this.db.from("action_executions").select("status").eq("idempotency_key", idempotencyKey)
      .single();
    if (data?.status === "executed") return "already_executed";
    if (data?.status === "pending") return "in_progress";
    // Previous attempt failed: re-claim atomically (only if still failed).
    const { data: reclaimed } = await this.db
      .from("action_executions")
      .update({ status: "pending", finished_at: null })
      .eq("idempotency_key", idempotencyKey)
      .eq("status", "failed")
      .select("idempotency_key");
    return reclaimed && reclaimed.length === 1 ? "claimed" : "in_progress";
  }

  async markApproved(actionId: string, draft: EmailDraftT, draftVersion: number, idempotencyKey: string) {
    const { error } = await this.db.from("actions").update({
      status: "approved",
      draft,
      draft_version: draftVersion,
      idempotency_key: idempotencyKey,
      failure_reason: null,
      updated_at: new Date().toISOString(),
    }).eq("id", actionId);
    if (error) throw new Error(`approve failed: ${error.message}`);
  }

  async finishExecution(
    actionId: string,
    idempotencyKey: string,
    outcome: { ok: true; providerId: string } | { ok: false; reason: string },
  ) {
    const now = new Date().toISOString();
    await this.db.from("action_executions").update({
      status: outcome.ok ? "executed" : "failed",
      provider_message_id: outcome.ok ? outcome.providerId : null,
      result_log: outcome.ok ? `sent ${outcome.providerId}` : outcome.reason,
      finished_at: now,
    }).eq("idempotency_key", idempotencyKey);
    await this.db.from("actions").update({
      status: outcome.ok ? "executed" : "failed",
      failure_reason: outcome.ok ? null : outcome.reason,
      updated_at: now,
    }).eq("id", actionId);
  }
}
