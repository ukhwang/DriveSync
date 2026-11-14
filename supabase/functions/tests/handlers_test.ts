// Fakes implement async ports with synchronous bodies on purpose.
// deno-lint-ignore-file require-await
import { assert, assertEquals } from "@std/assert";
import type { z } from "zod";
import { briefingHandler } from "../generate-briefing/handler.ts";
import { insightHandler } from "../generate-insight/handler.ts";
import { extractActionsHandler } from "../extract-actions/handler.ts";
import { executeActionHandler } from "../execute-action/handler.ts";
import { consentHandler } from "../record-consent/handler.ts";
import { serve } from "../_shared/http.ts";
import { type Llm, LlmError, type StructuredRequest } from "../_shared/llm.ts";
import { ResendMailer } from "../_shared/resend.ts";
import type {
  ActionRow,
  ClaimResult,
  Deps,
  Mailer,
  SendResult,
  ServiceRepo,
  UserRepo,
  VisitContext,
} from "../_shared/ports.ts";
import type { EmailDraftT, InsightT } from "../_shared/schemas.ts";
import { renderSchemas } from "../scripts/generate-json-schemas.ts";

// ------------------------------------------------------------------ fakes

const CTX: VisitContext = {
  visitId: "v-1",
  purpose: "2차 견적 협의",
  placeName: "한빛메디칼 본사",
  customerId: "c-hanbit",
  companyName: "한빛메디칼",
  contactName: "김도윤 구매팀장",
  contactEmail: "buyer@hanbit-medical.example",
  historySummary: "1차 방문: 납기 4주 요구",
  openActions: [],
};

class FakeUser implements UserRepo {
  readonly userId = "user-1";
  consent = true;
  quota = 100;
  insights = new Map<string, InsightT>();
  actions = new Map<string, ActionRow>();
  async hasConsent() {
    return this.consent;
  }
  async setConsent(granted: boolean) {
    this.consent = granted;
  }
  async consumeQuota() {
    return this.quota-- > 0;
  }
  async visitContext(visitId: string) {
    return visitId === CTX.visitId ? CTX : null;
  }
  async saveInsight(visitId: string, insight: InsightT) {
    this.insights.set(visitId, insight);
  }
  async getAction(actionId: string) {
    return this.actions.get(actionId) ?? null;
  }
}

class FakeService implements ServiceRepo {
  executions = new Map<string, "pending" | "executed" | "failed">();
  actionStatus = new Map<string, string>();
  inserted: { id: string; draft: EmailDraftT }[] = [];
  constructor(private readonly user: FakeUser) {}
  async insertDraftActions(_owner: string, _session: string, customerId: string, drafts: EmailDraftT[]) {
    const rows = drafts.map((draft, i) => ({ id: `act-${i + 1}`, draft }));
    rows.forEach((r) =>
      this.user.actions.set(r.id, { id: r.id, ownerId: this.user.userId, customerId, status: "draft" })
    );
    this.inserted.push(...rows);
    return rows;
  }
  async claimExecution(_owner: string, _action: string, key: string): Promise<ClaimResult> {
    const current = this.executions.get(key);
    if (current === "executed") return "already_executed";
    if (current === "pending") return "in_progress";
    this.executions.set(key, "pending");
    return "claimed";
  }
  async markApproved(actionId: string) {
    this.actionStatus.set(actionId, "approved");
  }
  async finishExecution(actionId: string, key: string, outcome: { ok: boolean }) {
    this.executions.set(key, outcome.ok ? "executed" : "failed");
    this.actionStatus.set(actionId, outcome.ok ? "executed" : "failed");
    const row = this.user.actions.get(actionId);
    if (row) row.status = outcome.ok ? "executed" : "failed";
  }
}

class FakeLlm implements Llm {
  next: unknown | LlmError = null;
  calls = 0;
  async structured<S extends z.ZodType>(_req: StructuredRequest<S>): Promise<z.infer<S>> {
    this.calls++;
    if (this.next instanceof LlmError) throw this.next;
    return this.next as z.infer<S>;
  }
}

class FakeMailer implements Mailer {
  results: SendResult[] = [];
  sent: { draft: EmailDraftT; key: string }[] = [];
  async send(draft: EmailDraftT, key: string): Promise<SendResult> {
    this.sent.push({ draft, key });
    return this.results.shift() ?? { ok: true, providerId: `msg-${this.sent.length}` };
  }
}

function setup(overrides: Partial<Deps> = {}) {
  const user = new FakeUser();
  const service = new FakeService(user);
  const llm = new FakeLlm();
  const mailer = new FakeMailer();
  const deps: Deps = {
    authenticate: (req) => Promise.resolve(req.headers.get("authorization") === "Bearer ok" ? user : null),
    service,
    llm,
    mailer,
    allowedRecipientDomains: [],
    ...overrides,
  };
  return { user, service, llm, mailer, deps };
}

function post(body: unknown, auth = "Bearer ok"): Request {
  return new Request("http://local/fn", {
    method: "POST",
    headers: { authorization: auth, "content-type": "application/json" },
    body: JSON.stringify(body),
  });
}

async function call(handler: (req: Request) => Promise<Response>, req: Request) {
  const res = await serve(handler)(req);
  return { status: res.status, body: await res.json() };
}

const line = (text: string, source: "user" | "ai" = "ai") => ({ text, source });
const VALID_INSIGHT = {
  summary: [line("납기 3주 확정이 목표", "user"), line("경쟁사 대비 가격 8% 차이"), line("구매팀장 결재 범위 확인")],
  concerns: [line("가격 민감도 높음")],
  expectedQuestions: [line("추가 할인 가능한가?")],
};
const DRAFT = {
  to: "buyer@hanbit-medical.example",
  subject: "수정 견적서 송부",
  body: "김도윤 팀장님, 수정 견적서를 보내드립니다.",
};

// ------------------------------------------------------------------ common guards

Deno.test("rejects invalid input with 400 before touching auth or LLM", async () => {
  const { deps, llm } = setup();
  const res = await call(insightHandler(deps), post({ visitId: "", travelNotes: "x" }));
  assertEquals(res.status, 400);
  assertEquals(res.body.error, "invalid_input");
  assertEquals(llm.calls, 0);
});

Deno.test("rejects missing auth with 401", async () => {
  const { deps } = setup();
  const res = await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: [] }, "Bearer nope"));
  assertEquals(res.status, 401);
});

Deno.test("requires LLM consent and records it", async () => {
  const { deps, user, llm } = setup();
  user.consent = false;
  const denied = await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: [] }));
  assertEquals(denied.status, 403);
  assertEquals(denied.body.error, "consent_required");
  assertEquals(llm.calls, 0);

  const granted = await call(consentHandler(deps), post({ granted: true }));
  assertEquals(granted.status, 200);
  assert(user.consent);
});

Deno.test("rate limit returns 429 with Retry-After", async () => {
  const { deps, user } = setup();
  user.quota = 0;
  const res = await serve(insightHandler(deps))(post({ visitId: "v-1", travelNotes: [] }));
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("retry-after"), "60");
});

// ------------------------------------------------------------------ insight / briefing

Deno.test("insight: valid output is stored and returned", async () => {
  const { deps, user, llm } = setup();
  llm.next = VALID_INSIGHT;
  const res = await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: ["가격 얘기 먼저"] }));
  assertEquals(res.status, 200);
  assertEquals(res.body.summary.length, 3);
  assert(user.insights.has("v-1"));
});

Deno.test("insight: output with 2 summary lines is rejected (422) and NOT stored", async () => {
  const { deps, user, llm } = setup();
  llm.next = { ...VALID_INSIGHT, summary: VALID_INSIGHT.summary.slice(0, 2) };
  const res = await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: [] }));
  assertEquals(res.status, 422);
  assertEquals(user.insights.size, 0);
});

Deno.test("insight: LLM refusal maps to 422, upstream outage to 502", async () => {
  const { deps, llm } = setup();
  llm.next = new LlmError("refusal", "declined");
  assertEquals((await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: [] }))).status, 422);
  llm.next = new LlmError("upstream", "down");
  assertEquals((await call(insightHandler(deps), post({ visitId: "v-1", travelNotes: [] }))).status, 502);
});

Deno.test("insight: unknown visit is 404", async () => {
  const { deps } = setup();
  assertEquals((await call(insightHandler(deps), post({ visitId: "v-x", travelNotes: [] }))).status, 404);
});

Deno.test("briefing: tier mismatch and missing core sentence are rejected", async () => {
  const { deps, llm } = setup();
  const base = {
    tier: "medium",
    screenLines: ["핵심 1"],
    voiceScript: [{ text: "오늘 목표는 납기 확정입니다.", core: true }],
    preArrivalRecap: "목표는 납기 확정, 질문은 결재 범위.",
  };
  llm.next = base;
  assertEquals(
    (await call(briefingHandler(deps), post({ visitId: "v-1", etaMinutes: 18, tier: "medium" }))).status,
    200,
  );
  llm.next = base;
  assertEquals((await call(briefingHandler(deps), post({ visitId: "v-1", etaMinutes: 5, tier: "short" }))).status, 422);
  llm.next = { ...base, voiceScript: [{ text: "부가 설명", core: false }] };
  assertEquals(
    (await call(briefingHandler(deps), post({ visitId: "v-1", etaMinutes: 18, tier: "medium" }))).status,
    422,
  );
});

// ------------------------------------------------------------------ debrief -> drafts

Deno.test("extract-actions stores drafts only and sends nothing", async () => {
  const { deps, llm, service, mailer } = setup();
  llm.next = { decisions: ["납기 3주 합의"], emails: [DRAFT] };
  const res = await call(
    extractActionsHandler(deps),
    post({ sessionId: crypto.randomUUID(), visitId: "v-1", debrief: "납기 3주 합의, 견적서 메일 발송" }),
  );
  assertEquals(res.status, 200);
  assertEquals(res.body.actions[0].type, "email");
  assertEquals(service.inserted.length, 1);
  assertEquals(mailer.sent.length, 0);
});

// ------------------------------------------------------------------ execution

function withAction(ctx: ReturnType<typeof setup>, id = "act-1") {
  ctx.user.actions.set(id, { id, ownerId: ctx.user.userId, customerId: "c-hanbit", status: "review" });
}
const execBody = (over: Record<string, unknown> = {}) => ({
  actionId: "act-1",
  idempotencyKey: "act-1:v1",
  draft: DRAFT,
  draftVersion: 1,
  ...over,
});

Deno.test("execute: success is reported only after the provider confirmed, and never sent twice", async () => {
  const ctx = setup();
  withAction(ctx);
  const first = await call(executeActionHandler(ctx.deps), post(execBody()));
  assertEquals(first.status, 200);
  assertEquals(first.body.status, "executed");
  const second = await call(executeActionHandler(ctx.deps), post(execBody()));
  assertEquals(second.body.status, "already_executed");
  assertEquals(ctx.mailer.sent.length, 1);
  assertEquals(ctx.mailer.sent[0]?.key, "act-1:v1");
});

Deno.test("execute: provider failure is 502 + failed, retry with same key then succeeds", async () => {
  const ctx = setup();
  withAction(ctx);
  ctx.mailer.results.push({ ok: false, reason: "mail provider unreachable: TimeoutError" });
  const failed = await call(executeActionHandler(ctx.deps), post(execBody()));
  assertEquals(failed.status, 502);
  assertEquals(failed.body.error, "send_failed");
  assertEquals(ctx.service.actionStatus.get("act-1"), "failed");

  const retried = await call(executeActionHandler(ctx.deps), post(execBody()));
  assertEquals(retried.body.status, "executed");
  assertEquals(ctx.mailer.sent.map((s) => s.key), ["act-1:v1", "act-1:v1"]);
});

Deno.test("execute: mismatched idempotency key, foreign action, blocked domain", async () => {
  const ctx = setup();
  withAction(ctx);
  assertEquals(
    (await call(executeActionHandler(ctx.deps), post(execBody({ idempotencyKey: "act-1:v2" })))).status,
    400,
  );
  assertEquals(
    (await call(executeActionHandler(ctx.deps), post(execBody({ actionId: "other", idempotencyKey: "other:v1" }))))
      .status,
    404,
  );

  const restricted = setup({ allowedRecipientDomains: ["drivesync.example"] });
  withAction(restricted);
  const blocked = await call(executeActionHandler(restricted.deps), post(execBody()));
  assertEquals(blocked.status, 422);
  assertEquals(blocked.body.error, "recipient_not_allowed");
  assertEquals(restricted.mailer.sent.length, 0);
});

Deno.test("resend mailer sends the idempotency key header and reports failures", async () => {
  const seen: Headers[] = [];
  const okFetch: typeof fetch = (_url, init) => {
    seen.push(new Headers(init?.headers));
    return Promise.resolve(new Response(JSON.stringify({ id: "re_123" }), { status: 200 }));
  };
  const ok = await new ResendMailer("key", "DriveSync <a@b.co>", okFetch).send(DRAFT, "act-1:v1");
  assertEquals(ok, { ok: true, providerId: "re_123" });
  assertEquals(seen[0]?.get("idempotency-key"), "act-1:v1");

  const downFetch: typeof fetch = () => Promise.reject(new TypeError("network"));
  const down = await new ResendMailer("key", "x <a@b.co>", downFetch).send(DRAFT, "k:v1");
  assertEquals(down.ok, false);
});

// ------------------------------------------------------------------ contracts

Deno.test("committed JSON Schemas match the Zod contracts", async () => {
  for (const [file, content] of Object.entries(renderSchemas())) {
    const committed = await Deno.readTextFile(new URL(`../../../schemas/${file}`, import.meta.url));
    assertEquals(committed, content, `schemas/${file} is stale: run \`deno task schemas\``);
  }
});
