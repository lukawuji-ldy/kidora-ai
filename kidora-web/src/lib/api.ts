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
  const body = (await res.json()) as ApiResponse<T>;
  if (!res.ok || body.code !== "OK") {
    throw new Error(body.message || body.code || `HTTP ${res.status}`);
  }
  return body.data as T;
}

export type TtsPayload = {
  audioBase64?: string;
  mimeType?: string;
  provider?: string;
};

export type PronunciationPayload = {
  overall?: number;
  accuracy?: number;
  fluency?: number;
  completeness?: number;
  provider?: string;
};

export type SseHandlers = {
  onDelta?: (text: string) => void;
  onSafety?: (text: string) => void;
  onError?: (text: string) => void;
  onTts?: (payload: TtsPayload) => void;
  onPronunciation?: (payload: PronunciationPayload) => void;
  onDone?: () => void;
};

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
        const data = line.slice(5).trim();
        if (eventName === "message.delta") handlers.onDelta?.(data);
        else if (eventName === "safety.block") handlers.onSafety?.(data);
        else if (eventName === "error") handlers.onError?.(data);
        else if (eventName === "audio.tts") {
          try {
            handlers.onTts?.(JSON.parse(data) as TtsPayload);
          } catch {
            handlers.onError?.("TTS 数据解析失败");
          }
        } else if (eventName === "pronunciation") {
          try {
            handlers.onPronunciation?.(JSON.parse(data) as PronunciationPayload);
          } catch {
            handlers.onError?.("发音评分数据解析失败");
          }
        } else if (eventName === "done") fireDone();
      } else if (line.trim() === "") {
        eventName = "message";
      }
    }
  }
  fireDone();
}
