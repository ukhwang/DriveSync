import { useState } from "react";

/** Failed -> (re-approve) Retry -> (connection back) Executed. Never shows success without a "server" result. */
export type RetryPhase = "failed" | "retry" | "executed";

export function useRetryFlow() {
  const [phase, setPhase] = useState<RetryPhase>("failed");
  return {
    phase,
    reapprove: () => setPhase((p) => (p === "failed" ? "retry" : p)),
    reconnect: () => setPhase((p) => (p === "retry" ? "executed" : p)),
    reset: () => setPhase("failed"),
  };
}
