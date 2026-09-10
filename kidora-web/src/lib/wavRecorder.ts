/** 目标采样率：与讯飞/腾讯短音频约定一致（16k 单声道 PCM/WAV）。 */
const TARGET_RATE = 16000;

export type WavCapture = {
  base64: string;
  mimeType: string;
  durationMs: number;
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

  get recording(): boolean {
    return this.processor != null;
  }

  async start(): Promise<void> {
    if (this.recording) return;
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error("当前浏览器不支持麦克风录音");
    }
    this.stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        channelCount: 1,
        echoCancellation: true,
        noiseSuppression: true,
      },
    });
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext;
    this.context = new AudioCtx();
    this.chunks = [];
    this.startedAt = Date.now();
    this.source = this.context.createMediaStreamSource(this.stream);
    // ScriptProcessor 仍是跨浏览器最简可靠路径；MVP 短句录音足够
    this.processor = this.context.createScriptProcessor(4096, 1, 1);
    this.processor.onaudioprocess = (ev) => {
      const input = ev.inputBuffer.getChannelData(0);
      this.chunks.push(new Float32Array(input));
    };
    this.source.connect(this.processor);
    // 必须接到 destination 才会触发 onaudioprocess；增益 0 避免麦克风回放
    const mute = this.context.createGain();
    mute.gain.value = 0;
    this.processor.connect(mute);
    mute.connect(this.context.destination);
  }

  async stop(): Promise<WavCapture> {
    if (!this.context || !this.processor) {
      throw new Error("尚未开始录音");
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

    const merged = mergeFloat32(this.chunks);
    this.chunks = [];
    const pcm16 = floatTo16BitPcm(resampleLinear(merged, sampleRate, TARGET_RATE));
    const wav = encodeWav(pcm16, TARGET_RATE);
    return {
      base64: bytesToBase64(wav),
      mimeType: "audio/wav",
      durationMs,
    };
  }

  cancel(): void {
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
