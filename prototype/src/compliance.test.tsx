/**
 * Proposal compliance checks for the demo. If one fails, the demo breaks a rule we promised
 * to the judges (proposal 1-①, 2-③, 3-⑥ and the submission privacy rules).
 */
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { SystemBar } from "./components/SystemBar";
import { BRIEF_CARDS, CHEAT_CARDS, SCENARIOS, VOICE_MEMO, briefingPlan, durationLabel } from "./data/demo";
import { PARKED_LABEL, SYSTEM_BARS } from "./data/systemBar";
import type { Approvals } from "./hooks/useApprovals";
import { State01Briefing } from "./states/State01Briefing";
import { State02Driving } from "./states/State02Driving";
import { State03VoiceMemo } from "./states/State03VoiceMemo";
import { State04CheatSheet } from "./states/State04CheatSheet";
import { State06Result } from "./states/State06Result";

const scenario = SCENARIOS[0]!;
const noop = () => {};

function draftApprovals(): Approvals {
  return {
    email: { status: "draft", version: 1, subject: "s", body: "b" },
    calendar: { status: "draft", version: 1 },
  };
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(tsx?|css|html)$/.test(name) && !name.endsWith(".test.tsx") ? [path] : [];
  });
}

describe("status bar (CarUxRestrictions is the single source of truth)", () => {
  it("every parked state names BOTH the UX restriction release and the P gear", () => {
    const parked = SYSTEM_BARS.filter((b) => b.mode === "parked");
    expect(parked.length).toBe(4);
    for (const bar of parked) {
      expect(bar.safety).toBe(PARKED_LABEL);
      const html = renderToStaticMarkup(<SystemBar config={bar} scenario={scenario} />);
      expect(html).toContain("CarUxRestrictions 해제");
      expect(html).toContain("P단");
    }
  });

  it("states 01-06 show the simulation-data badge, 07 shows no customer data", () => {
    SYSTEM_BARS.forEach((bar, index) => {
      const html = renderToStaticMarkup(<SystemBar config={bar} scenario={scenario} />);
      if (index < 6) expect(html, `state ${index + 1}`).toContain("시뮬레이션 데이터");
      else expect(html).not.toContain(scenario.name);
    });
  });
});

describe("source badges ([내 기록] / [AI 추론])", () => {
  it("every briefing and cheat-sheet line declares its origin", () => {
    for (const card of BRIEF_CARDS) expect(["user", "ai"]).toContain(card.source);
    for (const card of CHEAT_CARDS) {
      expect(card.lines.length).toBeGreaterThan(0);
      for (const line of card.lines) expect(["user", "ai"]).toContain(line.source);
    }
  });

  it("state 01 renders one origin badge per briefing card", () => {
    const html = renderToStaticMarkup(<State01Briefing scenario={scenario} onReady={noop} />);
    expect(html.match(/data-source=/g)?.length).toBe(BRIEF_CARDS.length);
  });

  it("the cheat sheet has the proposal's four sections and exactly 3 summary lines", () => {
    expect(CHEAT_CARDS.map((c) => c.id)).toEqual(["summary", "concerns", "questions", "memos"]);
    expect(CHEAT_CARDS[0]!.lines).toHaveLength(3);
  });
});

describe("driving screens stay minimal", () => {
  it("02 offers pause and a separate stop", () => {
    const html = renderToStaticMarkup(<State02Driving scenario={scenario} onVoiceMemo={noop} />);
    expect(html).toContain("일시정지");
    expect(html).toContain("중지");
  });

  it("03 never shows the transcript or AI output while driving", () => {
    const html = renderToStaticMarkup(<State03VoiceMemo />);
    expect(html).not.toContain(VOICE_MEMO.transcript);
    expect(html).not.toMatch(/자동 분류|LIVE TRANSCRIPT/);
    expect(html).toContain(`최대 ${durationLabel(VOICE_MEMO.maxSeconds)}`);
  });
});

describe("Human-in-the-Loop", () => {
  it("04 cheat sheet has no approve or send action", () => {
    const html = renderToStaticMarkup(<State04CheatSheet scenario={scenario} />);
    expect(html).not.toMatch(/승인|발송/);
  });

  it("06 executes nothing when nothing was approved", () => {
    const html = renderToStaticMarkup(
      <State06Result
        scenario={scenario}
        approvals={draftApprovals()}
        approvedIds={[]}
        phase="failed"
        onReapprove={noop}
        onReconnect={noop}
        onBackToApproval={noop}
      />,
    );
    expect(html).toContain("승인된 작업이 없습니다");
    expect(html).not.toContain("Executed");
  });

  it("06 failure path asks for re-approval instead of retrying on its own", () => {
    const html = renderToStaticMarkup(
      <State06Result
        scenario={scenario}
        approvals={{ ...draftApprovals(), calendar: { status: "approved", version: 1 } }}
        approvedIds={["calendar"]}
        phase="failed"
        onReapprove={noop}
        onReconnect={noop}
        onBackToApproval={noop}
      />,
    );
    expect(html).toContain("다시 승인 후 재시도");
    expect(html).toContain("ds-a-calendar:v1");
  });
});

describe("ETA-aware briefing (same thresholds as the Kotlin BriefingPlanner)", () => {
  it("maps ETA to 30 s / 60 s / 120 s", () => {
    expect(briefingPlan(9).seconds).toBe(30);
    expect(briefingPlan(10).seconds).toBe(60);
    expect(briefingPlan(24).seconds).toBe(60);
    expect(briefingPlan(25).seconds).toBe(120);
    expect(briefingPlan(9).recap).toBe(false);
  });
});

describe("privacy (submission rule: no real names, contacts or emails)", () => {
  it("contacts are masked and emails use the reserved .example domain", () => {
    for (const s of SCENARIOS) {
      expect(s.contact).toMatch(/^.OO /);
      expect(s.email).toMatch(/@[\w.-]+\.example$/);
    }
  });

  it("no source file contains an email outside .example", () => {
    const emailPattern = /[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g;
    const offenders = sourceFiles(join(__dirname))
      .flatMap((file) => (readFileSync(file, "utf8").match(emailPattern) ?? []).map((m) => `${file}: ${m}`))
      .filter((hit) => !/\.example$/.test(hit) && !/@(import|layer|media|keyframes|supports|font-face|container|theme|tailwind|apply)\b/.test(hit));
    expect(offenders).toEqual([]);
  });

  it("no phone numbers in the demo", () => {
    const phone = /01[016789]-?\d{3,4}-?\d{4}/;
    for (const file of sourceFiles(join(__dirname))) expect(readFileSync(file, "utf8"), file).not.toMatch(phone);
  });
});
