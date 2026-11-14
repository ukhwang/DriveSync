import type { Scenario } from "../data/demo";

export function DemoSwitcher({
  labels,
  active,
  onChange,
  scenarios,
  scenarioIndex,
  onScenarioChange,
}: {
  labels: readonly string[];
  active: number;
  onChange: (index: number) => void;
  scenarios: readonly Scenario[];
  scenarioIndex: number;
  onScenarioChange: (index: number) => void;
}) {
  return (
    <nav className="demo-switcher" aria-label="데모 상태 전환">
      <div className="product-lockup">
        <span className="product-dot" />
        <strong>DriveSync</strong>
        <span>드라이브싱크</span>
      </div>
      <div className="demo-tabs">
        {labels.map((label, index) => (
          <button key={label} className={active === index ? "active" : ""} onClick={() => onChange(index)}>
            <span>{String(index + 1).padStart(2, "0")}</span> {label}
          </button>
        ))}
      </div>
      <label className="scenario-switcher">
        <span>DATASET</span>
        <select
          value={scenarioIndex}
          onChange={(event) => onScenarioChange(Number(event.target.value))}
          aria-label="B2B 시나리오 데이터셋 (시뮬레이션)"
        >
          {scenarios.map((item, index) => (
            <option key={item.id} value={index}>
              시나리오 {item.id.toUpperCase()}: {item.name}
            </option>
          ))}
        </select>
      </label>
    </nav>
  );
}
