/**
 * Tutor TTS ↔ 自动听时序闸门：停播不得误触发 ended，播放中不得开麦。
 * @author liudy
 */

/** TTS 真正结束后的最小静默缓冲（音箱尾音 / BT 延迟）。 */
export const AUTO_LISTEN_AFTER_TTS_MS = 1200;

/**
 * audio.ended 后再保留的尾音保护（不立刻 revoke / 开麦），
 * 避免最后一音节被清理或抢麦掐掉。
 */
export const AUDIO_TAIL_MS = 500;

/** playTutorVoice 已做 ended 补齐后，scheduleAutoListen 仅短防抖。 */
export const AUTO_LISTEN_DEBOUNCE_MS = 200;

/** 外教仍在播时，延后重试开麦的间隔。 */
export const AUTO_LISTEN_RETRY_MS = 400;

/**
 * 播放世代：begin 拿到 token；stop / 新 begin 会使旧 token 失效。
 * 用于忽略 stopTutorVoice 清 src 时冒出的 error→onEnded。
 */
export class PlayGeneration {
  private seq = 0;
  private current = 0;

  begin(): number {
    this.current = ++this.seq;
    return this.current;
  }

  stop(): void {
    this.seq += 1;
    this.current = this.seq;
  }

  isLive(token: number): boolean {
    return token === this.current && token > 0;
  }
}

export type AutoListenArmOpts = {
  autoListen: boolean;
  readOnly: boolean;
  recording: boolean;
  /** 外教音频仍在播 */
  playbackLive: boolean;
};

export function shouldArmAutoListen(opts: AutoListenArmOpts): boolean {
  if (!opts.autoListen) return false;
  if (opts.readOnly) return false;
  if (opts.recording) return false;
  if (opts.playbackLive) return false;
  return true;
}

/**
 * 按朗读稿估算最短合理播报时长（儿童向语速偏慢）。
 * 中文按约 2.5 个英文单位计。
 */
export function estimateMinSpeakMs(speakable: string): number {
  const t = (speakable || "").trim();
  if (!t) return AUTO_LISTEN_AFTER_TTS_MS;
  let units = 0;
  for (const ch of t) {
    units += /[\u4e00-\u9fff]/.test(ch) ? 2.5 : 1;
  }
  return Math.max(AUTO_LISTEN_AFTER_TTS_MS, Math.round(units * 90));
}

/**
 * audio.ended 之后还需等待多久再通知业务层（开麦）。
 * 若实际播放偏短（截断/误 ended），用文案估算补齐，避免尾句未完就抢麦。
 * 并至少保留 AUDIO_TAIL_MS，避免尾音节被清理打断。
 */
export function postEndedHoldMs(elapsedMs: number, speakable: string): number {
  const minSpeak = estimateMinSpeakMs(speakable);
  const remain = Math.max(0, minSpeak - Math.max(0, elapsedMs));
  return Math.max(AUTO_LISTEN_AFTER_TTS_MS, AUDIO_TAIL_MS, remain);
}
