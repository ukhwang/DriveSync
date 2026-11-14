import type { Scenario } from "../data/demo";
import type { SystemBarConfig } from "../data/systemBar";
import { SimBadge } from "./Badges";
import { Icon } from "./Icon";

export function SystemBar({ config, scenario }: { config: SystemBarConfig; scenario: Scenario }) {
  if (config.workLabel) {
    return (
      <header className="system-bar work-mode" data-mode={config.mode}>
        <div className="work-brand">
          <span>∞</span> DriveSync
        </div>
        <div className="work-mode-meta">
          <span className="work-mode-badge">
            <i /> {config.safety} · {config.workLabel}
          </span>
          {config.simulated && <SimBadge />}
        </div>
      </header>
    );
  }
  return (
    <header className={`system-bar ${config.mode}`} data-mode={config.mode}>
      <div className="system-item safety">
        <Icon name={config.mode === "locked" ? "lock" : "shield"} size={26} />
        <span>{config.safety}</span>
      </div>
      <div className="system-separator" />
      <div className="system-item nav-status">
        <Icon name="route" size={26} />
        <span>{config.nav(scenario)}</span>
      </div>
      <div className="system-meta">
        {config.mode !== "locked" && (
          <span className={`gear ${config.mode === "driving" ? "drive" : ""}`}>
            {config.mode === "driving" ? "D" : "P"}
          </span>
        )}
        {config.simulated && <SimBadge />}
        <span className="system-time">10:42</span>
      </div>
    </header>
  );
}
