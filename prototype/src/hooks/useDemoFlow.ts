import { useState } from "react";
import { SCENARIOS, type Scenario } from "../data/demo";

export const STATE_COUNT = 7;

/** Which of the 7 demo states is shown, and which simulated dataset. */
export function useDemoFlow() {
  const [active, setActive] = useState(0);
  const [scenarioIndex, setScenarioIndex] = useState(0);
  const scenario: Scenario = SCENARIOS[scenarioIndex] ?? SCENARIOS[0]!;
  const go = (index: number) => setActive(Math.min(Math.max(index, 0), STATE_COUNT - 1));
  return { active, go, scenario, scenarioIndex, setScenarioIndex };
}
