/**
 * 本地 VAD 判定（纯函数）：起说确认、尾静音停麦、超时策略。
 * WavRecorder 只负责采音；门控参数与文案集中在此，便于单测。
 */

export const VAD_DEFAULTS = {
  /** 起说 RMS（0~1）；略低于旧值 0.008，照顾儿童远麦/轻声 */
  speechRms: 0.0035,
  /** 连续高于阈值多久算起说 */
  speechConfirmMs: 80,
  /** 起说后尾静音多久停麦 */
  trailingSilenceMs: 850,
  /** 最长聆听窗口 */
  maxListenMs: 15000,
  /** 最短有效说话时长 */
  minSpeechMs: 280,
  /**
   * 首尾裁切用的「采样峰值」阈值。必须远低于 speechRms：
   * trim 按 |sample| 判断，若与 RMS 同级会把轻声整段裁没 → 误报「没听到声音」。
   */
  silenceTrimPeak: 0.0006,
  /** 收尾是否可接受的 RMS（可略低于起说阈值） */
  acceptRms: 0.002,
  /** 低于阈值后允许短暂回落而不清零起说计时（抗字间空隙） */
  loudGapToleranceMs: 180,
} as const;

export type VadGateOpts = {
  speechRms: number;
  speechConfirmMs: number;
  trailingSilenceMs: number;
  maxListenMs: number;
  minSpeechMs: number;
  loudGapToleranceMs: number;
};

export type VadGateState = {
  armedAt: number;
  speechConfirmed: boolean;
  speechStartedAt: number;
  lastLoudAt: number;
  /** 当前连续响亮段起点；null 表示未在响亮段 */
  loudSince: number | null;
  /** 响亮段被短暂打断时的上次响亮时刻（用于 gap 容忍）；null 表示无 */
  loudHoldAt: number | null;
};

export type VadTickDecision =
  | { action: "continue"; state: VadGateState }
  | { action: "stop"; state: VadGateState }
  | { action: "timeout-empty"; state: VadGateState };

export function createVadGateState(armedAt: number): VadGateState {
  return {
    armedAt,
    speechConfirmed: false,
    speechStartedAt: 0,
    lastLoudAt: 0,
    loudSince: null,
    loudHoldAt: null,
  };
}

export function resolveVadOpts(
  partial: Partial<VadGateOpts> = {},
): VadGateOpts {
  return {
    speechRms: partial.speechRms ?? VAD_DEFAULTS.speechRms,
    speechConfirmMs: partial.speechConfirmMs ?? VAD_DEFAULTS.speechConfirmMs,
    trailingSilenceMs:
      partial.trailingSilenceMs ?? VAD_DEFAULTS.trailingSilenceMs,
    maxListenMs: partial.maxListenMs ?? VAD_DEFAULTS.maxListenMs,
    minSpeechMs: partial.minSpeechMs ?? VAD_DEFAULTS.minSpeechMs,
    loudGapToleranceMs:
      partial.loudGapToleranceMs ?? VAD_DEFAULTS.loudGapToleranceMs,
  };
}

/** 根据一帧 RMS 更新门控状态（不含超时停麦决策）。 */
export function applyVadFrame(
  prev: VadGateState,
  rms: number,
  now: number,
  opts: VadGateOpts,
): VadGateState {
  const next: VadGateState = { ...prev };
  if (rms >= opts.speechRms) {
    if (next.loudSince == null) {
      if (
        next.loudHoldAt != null &&
        now - next.loudHoldAt <= opts.loudGapToleranceMs
      ) {
        next.loudSince =
          next.speechStartedAt > 0 ? next.speechStartedAt : next.loudHoldAt;
      } else {
        next.loudSince = now;
      }
    }
    next.lastLoudAt = now;
    next.loudHoldAt = now;
    if (
      !next.speechConfirmed &&
      next.loudSince != null &&
      now - next.loudSince >= opts.speechConfirmMs
    ) {
      next.speechConfirmed = true;
      next.speechStartedAt = next.loudSince;
    }
  } else if (next.loudSince != null) {
    next.loudHoldAt = next.lastLoudAt || next.loudSince;
    next.loudSince = null;
  }
  return next;
}

/** 定时器 tick：在帧状态基础上决定是否停麦。 */
export function decideVadTick(
  state: VadGateState,
  now: number,
  opts: VadGateOpts,
): VadTickDecision {
  if (!state.speechConfirmed && now - state.armedAt >= opts.maxListenMs) {
    return { action: "timeout-empty", state };
  }
  if (state.speechConfirmed) {
    const loudMs = Math.max(0, state.lastLoudAt - state.speechStartedAt);
    const silentMs = now - state.lastLoudAt;
    // 有效响亮时长不含尾静音，避免「短噪声 + 尾静音」约 1s 误停麦
    if (silentMs >= opts.trailingSilenceMs && loudMs >= opts.minSpeechMs) {
      return { action: "stop", state };
    }
    if (now - state.armedAt >= opts.maxListenMs) {
      return { action: "stop", state };
    }
  }
  return { action: "continue", state };
}

/** 裁切首尾近静音（按采样峰值，阈值须远低于 speechRms）。 */
export function trimNearSilence(
  input: Float32Array,
  peakThreshold: number,
): Float32Array {
  if (input.length === 0) return input;
  let start = 0;
  let end = input.length - 1;
  while (start < input.length && Math.abs(input[start]) < peakThreshold) start++;
  while (end > start && Math.abs(input[end]) < peakThreshold) end--;
  if (start === 0 && end === input.length - 1) return input;
  if (start >= end) return new Float32Array(0);
  return input.subarray(start, end + 1);
}

export function rmsFloat(input: Float32Array): number {
  if (input.length === 0) return 0;
  let sum = 0;
  for (let i = 0; i < input.length; i++) {
    const v = input[i];
    sum += v * v;
  }
  return Math.sqrt(sum / input.length);
}

export function isAudibleFloat(input: Float32Array, minRms: number): boolean {
  return input.length > 0 && rmsFloat(input) >= minRms;
}

/** 录音器已拆除时的提示（避免误导为「尚未开始」）。 */
export const RECORDER_ALREADY_ENDED =
  "录音已结束，请再点「开始说话」说一句";

export const NO_SPEECH_HEARD =
  "没听到声音，请靠近麦克风再说一句，或检查是否静音";

export function recorderInactiveError(): Error {
  return new Error(RECORDER_ALREADY_ENDED);
}

export function noSpeechError(): Error {
  return new Error(NO_SPEECH_HEARD);
}
