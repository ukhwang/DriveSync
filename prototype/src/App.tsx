import { SCENARIOS } from "./data/demo";
import { STATE_LABELS, SYSTEM_BARS } from "./data/systemBar";
import { DemoSwitcher } from "./components/DemoSwitcher";
import { SystemBar } from "./components/SystemBar";
import { useApprovals } from "./hooks/useApprovals";
import { useDemoFlow } from "./hooks/useDemoFlow";
import { useRetryFlow } from "./hooks/useRetryFlow";
import { State01Briefing } from "./states/State01Briefing";
import { State02Driving } from "./states/State02Driving";
import { State03VoiceMemo } from "./states/State03VoiceMemo";
import { State04CheatSheet } from "./states/State04CheatSheet";
import { State05Approval } from "./states/State05Approval";
import { State06Result } from "./states/State06Result";
import { State07Locked } from "./states/State07Locked";

export default function App() {
  const flow = useDemoFlow();
  const { scenario } = flow;
  const approvals = useApprovals(scenario);
  const retry = useRetryFlow();
  const bar = SYSTEM_BARS[flow.active] ?? SYSTEM_BARS[0]!;

  const screens = [
    <State01Briefing scenario={scenario} onReady={() => flow.go(1)} />,
    <State02Driving scenario={scenario} onVoiceMemo={() => flow.go(2)} />,
    <State03VoiceMemo />,
    <State04CheatSheet scenario={scenario} />,
    <State05Approval
      scenario={scenario}
      approvals={approvals.approvals}
      onReview={approvals.startReview}
      onApprove={approvals.approve}
      onEditEmail={approvals.editEmail}
      approvedCount={approvals.approvedIds.length}
      onExecute={() => {
        retry.reset();
        flow.go(5);
      }}
    />,
    <State06Result
      scenario={scenario}
      approvals={approvals.approvals}
      approvedIds={approvals.approvedIds}
      phase={retry.phase}
      onReapprove={retry.reapprove}
      onReconnect={retry.reconnect}
      onBackToApproval={() => flow.go(4)}
    />,
    <State07Locked />,
  ];

  return (
    <div className="viewport">
      <div className="cockpit">
        <DemoSwitcher
          labels={STATE_LABELS}
          active={flow.active}
          onChange={flow.go}
          scenarios={SCENARIOS}
          scenarioIndex={flow.scenarioIndex}
          onScenarioChange={flow.setScenarioIndex}
        />
        <SystemBar config={bar} scenario={scenario} />
        <div className="screen-content" key={`${flow.active}-${scenario.id}`}>
          {screens[flow.active]}
        </div>
      </div>
    </div>
  );
}
