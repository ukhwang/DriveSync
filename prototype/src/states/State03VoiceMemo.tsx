import { useEffect, useMemo, useState } from "react";
import { VOICE_MEMO, durationLabel } from "../data/demo";
import { Icon } from "../components/Icon";

/** Simulated end of speech for the demo (the real app ends after a short silence or at the limit). */
const SPEECH_ENDS_AFTER = 8;

/** Only the last seconds are counted down, so a long limit does not pull the driver's eyes. */
const SHOW_COUNTDOWN_UNDER = 15;

/**
 * 03 이동 중 단발성 음성 메모 — one tap, one utterance, max VOICE_MEMO.maxSeconds (default 2 min, configurable).
 * While driving: NO transcript text, NO AI classification, NO follow-up questions.
 * Only the ring, a countdown and a confirmation. Content is reviewed after parking (state 04).
 */
export function State03VoiceMemo() {
  const [elapsed, setElapsed] = useState(0);
  const saved = elapsed >= SPEECH_ENDS_AFTER;

  useEffect(() => {
    if (saved) return;
    const timer = window.setInterval(() => setElapsed((value) => value + 1), 1000);
    return () => window.clearInterval(timer);
  }, [saved]);

  const waves = useMemo(() => Array.from({ length: 48 }, (_, i) => 16 + ((i * 17) % 34)), []);
  const remaining = VOICE_MEMO.maxSeconds - elapsed;

  return (
    <main className="voice-state multi-intent-voice">
      <div className="voice-heading">
        <span className="eyebrow">ONE-SHOT VOICE MEMO · 최대 {durationLabel(VOICE_MEMO.maxSeconds)} · 말이 끝나면 자동 저장</span>
        <h1>{saved ? "메모가 저장되었습니다" : "한 번 말씀하시면 자동 저장됩니다"}</h1>
      </div>
      <div
        className={`wave-ring compact ${saved ? "saved" : ""}`}
        aria-label={saved ? "음성 메모 저장 완료" : `음성 메모 녹음 중, ${durationLabel(remaining)} 남음`}
      >
        <div className="wave-bars">
          {waves.map((height, index) => (
            <i
              key={index}
              style={{
                height: saved ? 10 : height,
                transform: `translateX(-50%) rotate(${index * 7.5}deg) translateY(-188px)`,
                animationDelay: `${index * -0.035}s`,
              }}
            />
          ))}
        </div>
        <div className="ring-core">
          <Icon name={saved ? "check" : "mic"} size={42} />
          <strong>{saved ? "저장됨" : "듣는 중..."}</strong>
          <span>{saved ? "확인음 재생" : "카테고리 선택 불필요"}</span>
          {!saved && (
            <div className="countdown">
              {remaining <= SHOW_COUNTDOWN_UNDER ? `${remaining}초 남음` : `최대 ${durationLabel(VOICE_MEMO.maxSeconds)}`}
            </div>
          )}
        </div>
      </div>

      <div className="voice-safety">
        <Icon name="shield" size={22} />
        AI 응답·꼬리질문 없이 확인음만 냅니다. 내용은 정차(P) 후 치트시트의 &lsquo;이동 중 메모&rsquo;에서 확인합니다.
      </div>
    </main>
  );
}
