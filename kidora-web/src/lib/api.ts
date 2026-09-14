export const TOKEN_KEY = "kidora_token";

export type ApiResponse<T> = {
  code: string;
  message?: string;
  data?: T;
};

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiJson<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Content-Type", "application/json");
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(path, { ...init, headers });
  const raw = await res.text();
  let body: ApiResponse<T> | null = null;
  if (raw.trim()) {
    try {
      body = JSON.parse(raw) as ApiResponse<T>;
    } catch {
      throw new Error(res.ok ? "响应不是合法 JSON" : `HTTP ${res.status}`);
    }
  }
  if (res.status === 401) {
    clearToken();
    throw new Error(body?.message || body?.code || "未登录或令牌无效，请重新登录");
  }
  if (!res.ok || !body || body.code !== "OK") {
    throw new Error(body?.message || body?.code || `HTTP ${res.status}`);
  }
  return body.data as T;
}

/**
 * 带 JWT 拉取二进制（教具图等），返回可给 img 用的 blob URL；调用方负责 revoke。
 */
/**
 * 带 JWT 的图片走 fetch + blob，拿不到浏览器 HTTP 缓存，因此在模块级缓存 objectURL：
 * 同一 URL 一节课内只下载一次，组件卸载不再 revoke。
 */
const authedBlobCache = new Map<string, Promise<string>>();

export async function fetchAuthedBlobUrl(path: string): Promise<string> {
  const cached = authedBlobCache.get(path);
  if (cached) return cached;

  const pending = (async () => {
    const token = getToken();
    const res = await fetch(path, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    return URL.createObjectURL(await res.blob());
  })();

  authedBlobCache.set(path, pending);
  pending.catch(() => authedBlobCache.delete(path));
  return pending;
}

/** 预热缓存，失败静默（真正展示时会再报错并回退）。 */
export function prefetchAuthedBlob(path: string): void {
  void fetchAuthedBlobUrl(path).catch(() => undefined);
}

export type TtsPayload = {
  audioBase64?: string;
  mimeType?: string;
  provider?: string;
};

export type AsrPayload = {
  text?: string;
  locale?: string;
  provider?: string;
};

export type PronunciationPayload = {
  overall?: number;
  accuracy?: number;
  fluency?: number;
  completeness?: number;
  provider?: string;
};

export type TimingPayload = {
  schemaVersion?: number;
  kind?: string;
  path?: string;
  turnIndex?: number;
  [key: string]: unknown;
};

export type SseHandlers = {
  onDelta?: (text: string) => void;
  onSafety?: (text: string) => void;
  onError?: (text: string) => void;
  onTts?: (payload: TtsPayload) => void;
  onAsr?: (payload: AsrPayload) => void;
  onPronunciation?: (payload: PronunciationPayload) => void;
  onTiming?: (payload: TimingPayload) => void;
  onWrapUp?: (payload: { phase?: string; step?: number }) => void;
  onProp?: (payload: PropStagePayload) => void;
  onSessionCompleted?: (payload: {
    sessionId?: string;
    status?: string;
    childSummary?: string;
  }) => void;
  onDone?: () => void;
};

/** 后端判定的本轮教具舞台（SSE `turn.prop`）。 */
export type PropStagePayload = {
  layout?: string;
  activeLemma?: string | null;
  assets?: Array<{ lemma?: string; theme?: string; url?: string }>;
};

/** 单条 `data:` 行分发；流中与流尾残余行共用，避免两份重复分支。 */
function dispatchSseData(
  eventName: string,
  data: string,
  handlers: SseHandlers,
  fireDone: () => void,
): void {
  const parsed = <T,>(onParsed: (payload: T) => void, onFail?: () => void) => {
    try {
      onParsed(JSON.parse(data) as T);
    } catch {
      onFail?.();
    }
  };
  switch (eventName) {
    case "message.delta":
      handlers.onDelta?.(data);
      return;
    case "safety.block":
      handlers.onSafety?.(data);
      return;
    case "error":
      handlers.onError?.(data);
      return;
    case "asr.transcript":
      parsed<AsrPayload>((p) => handlers.onAsr?.(p), () => handlers.onError?.("ASR 数据解析失败"));
      return;
    case "audio.tts":
      parsed<TtsPayload>((p) => handlers.onTts?.(p), () => handlers.onError?.("TTS 数据解析失败"));
      return;
    case "pronunciation":
      parsed<PronunciationPayload>(
        (p) => handlers.onPronunciation?.(p),
        () => handlers.onError?.("发音评分数据解析失败"),
      );
      return;
    case "turn.timing":
      // 联调事件，解析失败忽略
      parsed<TimingPayload>((p) => handlers.onTiming?.(p));
      return;
    case "turn.prop":
      // 道具失败不影响陪练主流，退化为人像态
      parsed<PropStagePayload>((p) => handlers.onProp?.(p), () => handlers.onProp?.({}));
      return;
    case "session.wrapup":
      parsed<{ phase?: string; step?: number }>(
        (p) => handlers.onWrapUp?.(p),
        () => handlers.onWrapUp?.({}),
      );
      return;
    case "session.completed":
      parsed<{ sessionId?: string; status?: string; childSummary?: string }>(
        (p) => handlers.onSessionCompleted?.(p),
        () => handlers.onSessionCompleted?.({}),
      );
      return;
    case "done":
      fireDone();
      return;
    default:
  }
}

export async function postSse(
  path: string,
  body: unknown,
  handlers: SseHandlers,
): Promise<void> {
  const token = getToken();
  const res = await fetch(path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok || !res.body) {
    throw new Error(`SSE HTTP ${res.status}`);
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let eventName = "message";
  let doneFired = false;
  const fireDone = () => {
    if (doneFired) return;
    doneFired = true;
    handlers.onDone?.();
  };
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split(/\n/);
    buffer = parts.pop() || "";
    for (const line of parts) {
      if (line.startsWith("event:")) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        dispatchSseData(eventName, line.slice(5).trim(), handlers, fireDone);
      } else if (line.trim() === "") {
        eventName = "message";
      }
    }
  }
  // 流结束时若尾部缺换行，仍处理残余行，避免丢掉最后的 audio.tts
  if (buffer.length > 0) {
    const line = buffer;
    buffer = "";
    if (line.startsWith("event:")) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dispatchSseData(eventName, line.slice(5).trim(), handlers, fireDone);
    }
  }
  fireDone();
}

/**
 * 静默上报停麦→开播 e2e 耗时；失败最多再试 1 次。
 */
export async function postClientTiming(
  sessionId: string,
  turnIndex: number,
  e2eHeardMs: number,
): Promise<void> {
  const attempt = async () => {
    await apiJson(`/api/cet/sessions/${sessionId}/turns/${turnIndex}/client-timing`, {
      method: "POST",
      body: JSON.stringify({ e2eHeardMs: Math.round(e2eHeardMs) }),
    });
  };
  try {
    await attempt();
  } catch {
    try {
      await attempt();
    } catch {
      /* 不影响陪练 */
    }
  }
}
