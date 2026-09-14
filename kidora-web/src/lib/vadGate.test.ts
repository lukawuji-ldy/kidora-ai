import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  applyVadFrame,
  createVadGateState,
  decideVadTick,
  isAudibleFloat,
  RECORDER_ALREADY_ENDED,
  recorderInactiveError,
  resolveVadOpts,
  trimNearSilence,
  VAD_DEFAULTS,
} from "./vadGate.ts";

describe("vadGate", () => {
  const opts = resolveVadOpts();

  it("confirms soft child speech below old 0.008 threshold", () => {
    let state = createVadGateState(0);
    state = applyVadFrame(state, 0.005, 0, opts);
    state = applyVadFrame(state, 0.005, 80, opts);
    state = applyVadFrame(state, 0.005, 120, opts);
    assert.equal(state.speechConfirmed, true);
    assert.ok(VAD_DEFAULTS.speechRms < 0.008);
  });

  it("tolerates brief gaps between syllables without losing confirm progress", () => {
    let state = createVadGateState(0);
    state = applyVadFrame(state, 0.01, 0, opts);
    state = applyVadFrame(state, 0.01, 60, opts);
    state = applyVadFrame(state, 0.001, 100, opts);
    state = applyVadFrame(state, 0.01, 180, opts);
    assert.equal(state.speechConfirmed, true);
  });

  it("stops after trailing silence once speech confirmed", () => {
    let state = createVadGateState(0);
    state = applyVadFrame(state, 0.02, 0, opts);
    state = applyVadFrame(state, 0.02, 120, opts);
    // 持续说话至满足 minSpeechMs
    state = applyVadFrame(state, 0.02, 400, opts);
    assert.equal(state.speechConfirmed, true);
    state = applyVadFrame(state, 0.0001, 450, opts);
    const decision = decideVadTick(state, 450 + opts.trailingSilenceMs, opts);
    assert.equal(decision.action, "stop");
  });

  it("times out empty when never loud enough", () => {
    const state = createVadGateState(0);
    const decision = decideVadTick(state, opts.maxListenMs, opts);
    assert.equal(decision.action, "timeout-empty");
  });

  it("maps dead recorder to already-ended message not 尚未开始录音", () => {
    const err = recorderInactiveError();
    assert.equal(err.message, RECORDER_ALREADY_ENDED);
    assert.equal(err.message.includes("尚未开始录音"), false);
  });

  it("does not stop on brief noise blip plus trailing silence", () => {
    let state = createVadGateState(0);
    // 80ms 噪声起说确认
    state = applyVadFrame(state, 0.02, 0, opts);
    state = applyVadFrame(state, 0.02, 90, opts);
    assert.equal(state.speechConfirmed, true);
    // 立刻变静
    state = applyVadFrame(state, 0.0001, 100, opts);
    const decision = decideVadTick(state, 100 + opts.trailingSilenceMs, opts);
    // 有效响亮时长不足 minSpeechMs，应继续听而不是停麦误报
    assert.equal(decision.action, "continue");
  });

  it("does not trim away soft speech when peak is near speechRms", () => {
    const n = 4000;
    const buf = new Float32Array(n + 800);
    for (let i = 0; i < 400; i++) buf[i] = 0;
    for (let i = 0; i < n; i++) {
      buf[400 + i] = Math.sin(i / 8) * 0.006;
    }
    for (let i = 0; i < 400; i++) buf[400 + n + i] = 0;

    assert.ok(
      VAD_DEFAULTS.silenceTrimPeak < VAD_DEFAULTS.speechRms,
      "trim peak must be looser than speech RMS gate",
    );
    const trimmed = trimNearSilence(buf, VAD_DEFAULTS.silenceTrimPeak);
    assert.ok(trimmed.length > 1000, `soft speech should survive trim, got ${trimmed.length}`);
    assert.equal(isAudibleFloat(trimmed, VAD_DEFAULTS.acceptRms), true);
  });
});
