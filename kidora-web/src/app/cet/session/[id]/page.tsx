"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  apiJson,
  getToken,
  postSse,
  PronunciationPayload,
  TtsPayload,
} from "@/lib/api";
import { WavRecorder } from "@/lib/wavRecorder";

type Bubble = {
  id: string;
  role: "child" | "tutor" | "system";
  text: string;
  scores?: PronunciationPayload;
};

let sharedAudio: HTMLAudioElement | null = null;

function playTts(payload: TtsPayload) {
  if (!payload.audioBase64) return;
  const mime = payload.mimeType || "audio/mpeg";
  const src = `data:${mime};base64,${payload.audioBase64}`;
  if (sharedAudio) {
    sharedAudio.pause();
    sharedAudio.src = "";
  }
  const audio = new Audio(src);
  sharedAudio = audio;
  void audio.play().catch(() => {
    /* 自动播放可能被浏览器拦截；用户可再点播放时再扩 */
  });
}

export default function CetSessionPage() {
  const params = useParams<{ id: string }>();
  const sessionId = params.id;
  const router = useRouter();
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [error, setError] = useState("");
  const recorderRef = useRef<WavRecorder | null>(null);
  const planSummary = useMemo(() => {
    if (typeof window === "undefined") return "";
    return localStorage.getItem(`cet_plan_${sessionId}`) || "";
  }, [sessionId]);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    else if (planSummary) {
      setBubbles([
        {
          id: "plan",
          role: "system",
          text: planSummary,
        },
      ]);
    }
    return () => {
      recorderRef.current?.cancel();
      if (sharedAudio) {
        sharedAudio.pause();
        sharedAudio = null;
      }
    };
  }, [router, planSummary]);

  function lastTutorText(): string | undefined {
    for (let i = bubbles.length - 1; i >= 0; i--) {
      const b = bubbles[i];
      if (b.role === "tutor" && b.text.trim()) return b.text.trim();
    }
    return undefined;
  }

  async function streamTurn(
    body: { text?: string; audioBase64?: string; locale?: string; referenceText?: string },
    childLabel: string,
  ) {
    setError("");
    setBusy(true);
    const childId = `c-${Date.now()}`;
    const tutorId = `t-${Date.now()}`;
    setBubbles((prev) => [
      ...prev,
      { id: childId, role: "child", text: childLabel },
      { id: tutorId, role: "tutor", text: "" },
    ]);
    try {
      await postSse(`/api/cet/sessions/${sessionId}/stream`, body, {
        onDelta: (chunk) => {
          setBubbles((prev) =>
            prev.map((b) =>
              b.id === tutorId ? { ...b, text: b.text + chunk } : b,
            ),
          );
        },
        onSafety: (msg) => {
          setBubbles((prev) =>
            prev.map((b) =>
              b.id === tutorId
                ? { ...b, role: "system", text: "我们换个更有趣的话题聊聊吧～" }
                : b,
            ),
          );
          setError(msg);
        },
        onError: (msg) => setError(msg),
        onTts: (payload) => playTts(payload),
        onPronunciation: (scores) => {
          setBubbles((prev) =>
            prev.map((b) => (b.id === tutorId ? { ...b, scores } : b)),
          );
        },
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "发送失败");
    } finally {
      setBusy(false);
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || busy || recording) return;
    const childText = text.trim();
    setText("");
    await streamTurn({ text: childText }, childText);
  }

  async function toggleRecord() {
    if (busy) return;
    if (recording) {
      try {
        const rec = recorderRef.current;
        if (!rec) return;
        const capture = await rec.stop();
        recorderRef.current = null;
        setRecording(false);
        if (capture.durationMs < 400) {
          setError("录音太短，请再说一句试试");
          return;
        }
        const referenceText = lastTutorText();
        await streamTurn(
          {
            audioBase64: capture.base64,
            locale: "en-US",
            ...(referenceText ? { referenceText } : {}),
          },
          "（语音）",
        );
      } catch (err) {
        setRecording(false);
        recorderRef.current = null;
        setError(err instanceof Error ? err.message : "录音失败");
      }
      return;
    }
    try {
      setError("");
      const rec = new WavRecorder();
      await rec.start();
      recorderRef.current = rec;
      setRecording(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "无法打开麦克风");
    }
  }

  async function complete() {
    setBusy(true);
    setError("");
    try {
      await apiJson(`/api/cet/sessions/${sessionId}/complete`, {
        method: "POST",
        body: "{}",
      });
      router.push(`/cet/report/${sessionId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "结课失败");
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/home">
          Kidora · CET
        </Link>
        <button className="btn ghost" type="button" onClick={complete} disabled={busy}>
          结束练习
        </button>
      </div>
      <div className="panel">
        <div className="chat-log">
          {bubbles.map((b) => (
            <div key={b.id} className={`bubble ${b.role}`}>
              {b.text || (busy && b.role === "tutor" ? "…" : "")}
              {b.scores ? (
                <div className="pron-scores">
                  发音 {fmtScore(b.scores.overall)} · 准确{" "}
                  {fmtScore(b.scores.accuracy)} · 流利 {fmtScore(b.scores.fluency)} ·
                  完整 {fmtScore(b.scores.completeness)}
                </div>
              ) : null}
            </div>
          ))}
        </div>
        {error ? <p className="error">{error}</p> : null}
        <form className="composer" onSubmit={send}>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="用英语或中文说说看…"
            disabled={busy || recording}
          />
          <button
            className={`btn ${recording ? "recording" : "ghost"}`}
            type="button"
            onClick={toggleRecord}
            disabled={busy}
            aria-pressed={recording}
          >
            {recording ? "停止并发送" : "录音"}
          </button>
          <button
            className="btn"
            type="submit"
            disabled={busy || recording || !text.trim()}
          >
            {busy ? "思考中" : "发送"}
          </button>
        </form>
      </div>
    </main>
  );
}

function fmtScore(n?: number): string {
  if (n == null || Number.isNaN(n)) return "-";
  return String(Math.round(n));
}
