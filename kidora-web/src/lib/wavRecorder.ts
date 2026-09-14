import {
  applyVadFrame,
  createVadGateState,
  decideVadTick,
  isAudibleFloat,
  noSpeechError,
  recorderInactiveError,
  resolveVadOpts,
  trimNearSilence,
  VAD_DEFAULTS,
  type VadGateOpts,
  type VadGateState,
} from "./vadGate";

/** 目标采样率：与讯飞/腾讯短音频约定一致（16k 单声道 PCM/WAV）。 */
const TARGET_RATE = 16000;

export type WavCapture = {
  base64: string;
  mimeType: string;
  durationMs: number;
};

export type VadListenOptions = {
  /** RMS threshold (0~1). Default from VAD_DEFAULTS */
  speechRms?: number;
  /** ms above threshold to confirm speech */
  speechConfirmMs?: number;
  /** trailing silence after speech before stop */
  trailingSilenceMs?: number;
  /** max listen window */
  maxListenMs?: number;
  /** reject if shorter */
  minSpeechMs?: number;
  onLevel?: (rms: number) => void;
};

/**
 * 浏览器麦克风 → 16k mono WAV（base64）。
 * 避免 MediaRecorder 默认 webm/m4a（讯飞拒 m4a）。
 */
export class WavRecorder {
  private stream: MediaStream | null = null;
  private context: AudioContext | null = null;
  private processor: ScriptProcessorNode | null = null;
  private source: MediaStreamAudioSourceNode | null = null;
  private chunks: Float32Array[] = [];
  private startedAt = 0;
  private levelCb: ((rms: number) => void) | null = null;
  private vadTimer: ReturnType<typeof setInterval> | null = null;
  private vadOpts: VadGateOpts | null = null;
  private gate: VadGateState | null = null;
  private vadResolve: ((c: WavCapture) => void) | null = null;
  private vadReject: ((e: Error) => void) | null = null;
  private vadClosing = false;

  get recording(): boolean {
    return this.processor != null;
  }

  async start(): Promise<void> {
    if (this.recording) return;
    await this.openMic();
    this.chunks = [];
    this.startedAt = Date.now();
    this.gate = null;
    this.attachProcessor((input) => {
      this.chunks.push(new Float32Array(input));
      if (this.levelCb) {
        this.levelCb(rmsFloat32(input));
      }
    });
  }

  /**
   * Hands-free: open mic, wait for speech then trailing silence, then return WAV.
   * Never resolves with near-silent audio (rejects locally — no upload).
   */
  async listenUntilSilence(opts: VadListenOptions = {}): Promise<WavCapture> {
    if (this.recording) {
      throw new Error("已在录音中");
    }
    const vad = resolveVadOpts({
      speechRms: opts.speechRms,
      speechConfirmMs: opts.speechConfirmMs,
      trailingSilenceMs: opts.trailingSilenceMs,
      maxListenMs: opts.maxListenMs,
      minSpeechMs: opts.minSpeechMs,
    });
    this.vadOpts = vad;
    this.levelCb = opts.onLevel ?? null;
    await this.openMic();
    this.chunks = [];
    this.startedAt = Date.now();
    this.gate = createVadGateState(Date.now());
    this.vadClosing = false;

    this.attachProcessor((input) => {
      this.chunks.push(new Float32Array(input));
      const rms = rmsFloat32(input);
      this.levelCb?.(rms);
      if (!this.gate || !this.vadOpts) return;
      this.gate = applyVadFrame(this.gate, rms, Date.now(), this.vadOpts);
    });

    return new Promise<WavCapture>((resolve, reject) => {
      this.vadResolve = resolve;
      this.vadReject = reject;
      this.vadTimer = setInterval(() => {
        void this.tickVad();
      }, 80);
    });
  }

  private async tickVad(): Promise<void> {
    const vad = this.vadOpts;
    const gate = this.gate;
    if (!vad || !gate || !this.recording || this.vadClosing) return;
    const decision = decideVadTick(gate, Date.now(), vad);
    if (decision.action === "continue") return;
    if (decision.action === "timeout-empty") {
      await this.failVad(noSpeechError().message);
      return;
    }
    this.vadClosing = true;
    try {
      const capture = await this.finishCapture();
      const resolve = this.vadResolve;
      this.clearVadWaiters();
      resolve?.(capture);
    } catch (e) {
      const reject = this.vadReject;
      this.clearVadWaiters();
      reject?.(e instanceof Error ? e : new Error("录音失败"));
    }
  }

  private async failVad(message: string): Promise<void> {
    if (this.vadClosing) return;
    this.vadClosing = true;
    this.cancel(new Error(message));
  }

  private clearVadWaiters(): void {
    if (this.vadTimer) {
      clearInterval(this.vadTimer);
      this.vadTimer = null;
    }
    this.vadOpts = null;
    this.gate = null;
    this.vadResolve = null;
    this.vadReject = null;
    this.levelCb = null;
    this.vadClosing = false;
  }

  async stop(): Promise<WavCapture> {
    if (this.vadClosing && !this.recording) {
      throw recorderInactiveError();
    }
    if (this.vadResolve) {
      // 未确认起说也先尝试收尾：轻声可能刚过门控边缘，避免直接误报没听到
      this.vadClosing = true;
      try {
        const capture = await this.finishCapture();
        const reject = this.vadReject;
        this.clearVadWaiters();
        reject?.(recorderInactiveError());
        return capture;
      } catch (e) {
        const reject = this.vadReject;
        this.clearVadWaiters();
        const err =
          e instanceof Error
            ? e
            : this.gate?.speechConfirmed
              ? new Error("录音失败")
              : noSpeechError();
        reject?.(err);
        throw err;
      }
    }
    if (!this.recording) {
      throw recorderInactiveError();
    }
    return this.finishCapture();
  }

  private async finishCapture(): Promise<WavCapture> {
    if (!this.context || !this.processor) {
      throw recorderInactiveError();
    }
    const sampleRate = this.context.sampleRate;
    const durationMs = Math.max(0, Date.now() - this.startedAt);
    this.processor.disconnect();
    this.source?.disconnect();
    this.processor.onaudioprocess = null;
    this.processor = null;
    this.source = null;
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    await this.context.close().catch(() => undefined);
    this.context = null;

    let merged = mergeFloat32(this.chunks);
    this.chunks = [];
    // 先看未裁切能量，避免 trim 误伤轻声
    if (!isAudibleFloat(merged, VAD_DEFAULTS.acceptRms)) {
      throw noSpeechError();
    }
    merged = trimNearSilence(merged, VAD_DEFAULTS.silenceTrimPeak);
    if (!isAudibleFloat(merged, VAD_DEFAULTS.acceptRms)) {
      throw noSpeechError();
    }
    const pcm16 = floatTo16BitPcm(resampleLinear(merged, sampleRate, TARGET_RATE));
    if (pcm16.length === 0 || rmsPcm16(pcm16) < VAD_DEFAULTS.acceptRms) {
      throw noSpeechError();
    }
    if (durationMs < VAD_DEFAULTS.minSpeechMs) {
      throw new Error("录音太短，请再说一句试试");
    }
    const wav = encodeWav(pcm16, TARGET_RATE);
    return {
      base64: bytesToBase64(wav),
      mimeType: "audio/wav",
      durationMs,
    };
  }

  /**
   * 拆除麦克风与 VAD；若仍有 listenUntilSilence 挂起则 reject。
   * @param reason 自定义拒绝原因；默认「录音已结束」（结课/离页取消）
   */
  cancel(reason?: Error): void {
    const reject = this.vadReject;
    if (this.vadTimer) {
      clearInterval(this.vadTimer);
      this.vadTimer = null;
    }
    this.vadOpts = null;
    this.gate = null;
    this.vadResolve = null;
    this.vadReject = null;
    this.levelCb = null;
    this.vadClosing = false;
    this.processor?.disconnect();
    this.source?.disconnect();
    if (this.processor) this.processor.onaudioprocess = null;
    this.processor = null;
    this.source = null;
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
    this.chunks = [];
    void this.context?.close().catch(() => undefined);
    this.context = null;
    reject?.(reason ?? recorderInactiveError());
  }

  private async openMic(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error("当前浏览器不支持麦克风录音");
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
        },
      });
    } catch (err) {
      const name = err instanceof DOMException ? err.name : "";
      const msg = err instanceof Error ? err.message : "";
      if (
        name === "NotAllowedError" ||
        name === "PermissionDeniedError" ||
        /permission|denied|dismissed/i.test(msg)
      ) {
        throw new Error("麦克风权限未开启，请在浏览器允许后重试，或改用文字输入");
      }
      if (name === "NotFoundError") {
        throw new Error("未检测到麦克风设备");
      }
      throw err instanceof Error ? err : new Error("无法打开麦克风");
    }
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext;
    this.context = new AudioCtx();
    if (this.context.state === "suspended") {
      await this.context.resume();
    }
    this.source = this.context.createMediaStreamSource(this.stream);
  }

  private attachProcessor(onData: (input: Float32Array) => void): void {
    if (!this.context || !this.source) {
      throw new Error("麦克风未就绪");
    }
    this.processor = this.context.createScriptProcessor(4096, 1, 1);
    this.processor.onaudioprocess = (ev) => {
      onData(ev.inputBuffer.getChannelData(0));
    };
    this.source.connect(this.processor);
    const mute = this.context.createGain();
    mute.gain.value = 0;
    this.processor.connect(mute);
    mute.connect(this.context.destination);
  }
}

function mergeFloat32(chunks: Float32Array[]): Float32Array {
  let len = 0;
  for (const c of chunks) len += c.length;
  const out = new Float32Array(len);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

function resampleLinear(
  input: Float32Array,
  fromRate: number,
  toRate: number,
): Float32Array {
  if (fromRate === toRate || input.length === 0) return input;
  const ratio = fromRate / toRate;
  const outLen = Math.max(1, Math.round(input.length / ratio));
  const out = new Float32Array(outLen);
  for (let i = 0; i < outLen; i++) {
    const src = i * ratio;
    const i0 = Math.floor(src);
    const i1 = Math.min(i0 + 1, input.length - 1);
    const t = src - i0;
    out[i] = input[i0] * (1 - t) + input[i1] * t;
  }
  return out;
}

function floatTo16BitPcm(input: Float32Array): Int16Array {
  const out = new Int16Array(input.length);
  for (let i = 0; i < input.length; i++) {
    const s = Math.max(-1, Math.min(1, input[i]));
    out[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
  }
  return out;
}

function rmsFloat32(input: Float32Array): number {
  if (input.length === 0) return 0;
  let sum = 0;
  for (let i = 0; i < input.length; i++) {
    const v = input[i];
    sum += v * v;
  }
  return Math.sqrt(sum / input.length);
}

/** PCM16 RMS（0~1）；近 0 视为静音。 */
function rmsPcm16(pcm: Int16Array): number {
  if (pcm.length === 0) return 0;
  let sum = 0;
  for (let i = 0; i < pcm.length; i++) {
    const v = pcm[i] / 32768;
    sum += v * v;
  }
  return Math.sqrt(sum / pcm.length);
}

function encodeWav(pcm: Int16Array, sampleRate: number): Uint8Array {
  const dataSize = pcm.length * 2;
  const buffer = new ArrayBuffer(44 + dataSize);
  const view = new DataView(buffer);
  writeString(view, 0, "RIFF");
  view.setUint32(4, 36 + dataSize, true);
  writeString(view, 8, "WAVE");
  writeString(view, 12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true);
  view.setUint16(32, 2, true);
  view.setUint16(34, 16, true);
  writeString(view, 36, "data");
  view.setUint32(40, dataSize, true);
  let offset = 44;
  for (let i = 0; i < pcm.length; i++, offset += 2) {
    view.setInt16(offset, pcm[i], true);
  }
  return new Uint8Array(buffer);
}

function writeString(view: DataView, offset: number, s: string) {
  for (let i = 0; i < s.length; i++) view.setUint8(offset + i, s.charCodeAt(i));
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}
