import { Icon } from "../components/Icon";

/** 07 PAUSED_BY_POLICY — vehicle state is untrustworthy, so the screen is treated as driving. */
export function State07Locked() {
  return (
    <main className="lock-state">
      <div className="lock-halo">
        <Icon name="lock" size={76} />
      </div>
      <section className="lock-card">
        <span className="lock-code">PAUSED_BY_POLICY</span>
        <h1>
          안전을 위해 화면 조작이
          <br />
          일시 잠금되었습니다
        </h1>
        <p>
          차량 주행 제한 상태(CarUxRestrictions) 신호가 불확실하여 주행 제한 상태로 간주합니다.
          <br />
          현재 작성 중인 세션은 로컬에 안전하게 보존됩니다.
        </p>
        <div className="lock-status">
          <span className="pulse-dot" />
          <strong>차량 상태 신호 재확인 중</strong>
          <small>신호가 복구되면 이전 단계로 자동 복원됩니다.</small>
        </div>
      </section>
      <div className="preserved">
        <Icon name="database" size={24} />
        <span>
          <strong>세션 보존 완료</strong>SQLCipher 암호화 로컬 저장소
        </span>
      </div>
    </main>
  );
}
