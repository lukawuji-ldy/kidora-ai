/**
 * 外教 TTS 播放：blob URL + 播放世代，避免 stop 清 src 误触发 onEnded。
 * ended 后按文案估算补齐静默，防止短音频/误 ended 抢麦。
 * @author liudy
 */

import {
  PlayGeneration,
  postEndedHoldMs,
} from "./tutorVoiceGate";

export type TutorPlayOpts = {
  text?: string;
  audioBase64?: string;
  mimeType?: string;
  onBlocked?: () => void;
  onStart?: () => void;
  onEnded?: () => void;
  allowBrowserFallback?: boolean;
};

let sharedAudio: HTMLAudioElement | null = null;
let sharedObjectUrl: string | null = null;
let holdTimer: ReturnType<typeof setTimeout> | null = null;
const playGen = new PlayGeneration();

/** Strip Chinese parenthetical hints so browser/MCP voice reads English only. */
export function speakableText(text: string): string {
  const stripped = text
    .replace(/[（(][^）)]*[\u4e00-\u9fff][^）)]*[）)]/g, " ")
    .replace(/\s{2,}/g, " ")
    .trim();
  return stripped || text.trim();
}

function revokeObjectUrl() {
  if (sharedObjectUrl) {
    URL.revokeObjectURL(sharedObjectUrl);
    sharedObjectUrl = null;
  }
}

function clearHoldTimer() {
  if (holdTimer != null) {
    clearTimeout(holdTimer);
    holdTimer = null;
  }
}

function base64ToObjectUrl(audioBase64: string, mimeType: string): string {
  const binary = atob(audioBase64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  const blob = new Blob([bytes], { type: mimeType || "audio/mpeg" });
  return URL.createObjectURL(blob);
}

/** True while a tutor HTMLAudioElement is actively playing. */
export function isTutorVoicePlaying(): boolean {
  const a = sharedAudio;
  if (!a) return false;
  return !a.paused && !a.ended;
}

/**
 * Stop current tutor audio without invoking its onEnded (generation invalidated first).
 */
export function stopTutorVoice() {
  playGen.stop();
  clearHoldTimer();
  if (typeof window !== "undefined" && window.speechSynthesis) {
    window.speechSynthesis.cancel();
  }
  if (sharedAudio) {
    const a = sharedAudio;
    sharedAudio = null;
    try {
      a.pause();
      a.removeAttribute("src");
      a.load();
    } catch {
      /* ignore */
    }
  }
  revokeObjectUrl();
}

function speakBrowser(
  text: string,
  token: number,
  onStart?: () => void,
  onEnded?: () => void,
): boolean {
  if (!text || typeof window === "undefined" || !window.speechSynthesis) {
    return false;
  }
  try {
    const utter = new SpeechSynthesisUtterance(text);
    utter.lang = "en-US";
    utter.rate = 0.92;
    utter.onstart = () => {
      if (playGen.isLive(token)) onStart?.();
    };
    utter.onend = () => {
      if (playGen.isLive(token)) onEnded?.();
    };
    utter.onerror = () => {
      if (playGen.isLive(token)) onEnded?.();
    };
    window.speechSynthesis.speak(utter);
    return true;
  } catch {
    return false;
  }
}

function scheduleEnded(
  token: number,
  startedAt: number,
  speakable: string,
  onEnded?: () => void,
) {
  if (!playGen.isLive(token)) return;
  const elapsed = performance.now() - startedAt;
  const hold = postEndedHoldMs(elapsed, speakable);
  clearHoldTimer();
  holdTimer = setTimeout(() => {
    holdTimer = null;
    if (!playGen.isLive(token)) return;
    playGen.stop();
    onEnded?.();
  }, hold);
}

/** Prefer MCP TTS audio; do not fall back to browser English voice for tutor lines. */
export function playTutorVoice(opts: TutorPlayOpts) {
  stopTutorVoice();
  const token = playGen.begin();
  const text = speakableText(opts.text || "");
  const startedAt = performance.now();

  const finishIfLive = (immediate = false) => {
    if (!playGen.isLive(token)) return;
    if (immediate) {
      clearHoldTimer();
      playGen.stop();
      opts.onEnded?.();
      return;
    }
    scheduleEnded(token, startedAt, text, opts.onEnded);
  };
  const startIfLive = () => {
    if (!playGen.isLive(token)) return;
    opts.onStart?.();
  };

  if (opts.audioBase64) {
    const mime = opts.mimeType || "audio/mpeg";
    let src: string;
    try {
      src = base64ToObjectUrl(opts.audioBase64, mime);
      sharedObjectUrl = src;
    } catch {
      if (opts.allowBrowserFallback && speakBrowser(text, token, startIfLive, () => finishIfLive(true))) {
        return;
      }
      opts.onBlocked?.();
      finishIfLive(true);
      return;
    }
    const audio = new Audio(src);
    sharedAudio = audio;
    let started = false;
    const markStart = () => {
      if (started || !playGen.isLive(token)) return;
      started = true;
      startIfLive();
    };
    audio.addEventListener("playing", markStart);
    audio.addEventListener("timeupdate", () => {
      if (audio.currentTime > 0) markStart();
    });
    audio.addEventListener("ended", () => {
      if (!playGen.isLive(token)) return;
      // 不在 ended 时立刻 revoke：部分浏览器尾帧仍在冲刷，revoke 会掐掉最后一音节
      if (sharedAudio === audio) sharedAudio = null;
      finishIfLive(false);
    });
    audio.addEventListener("error", () => {
      if (!playGen.isLive(token)) return;
      if (sharedAudio === audio) sharedAudio = null;
      revokeObjectUrl();
      if (!started) opts.onBlocked?.();
      finishIfLive(true);
    });
    void audio.play().then(markStart).catch(() => {
      if (!playGen.isLive(token)) return;
      if (opts.allowBrowserFallback && speakBrowser(text, token, startIfLive, () => finishIfLive(true))) {
        return;
      }
      opts.onBlocked?.();
      finishIfLive(true);
    });
    return;
  }

  if (opts.allowBrowserFallback && speakBrowser(text, token, startIfLive, () => finishIfLive(true))) {
    return;
  }
  opts.onBlocked?.();
  finishIfLive(true);
}
