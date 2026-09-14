"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiJson, getToken, TtsPayload } from "@/lib/api";

type SessionDetail = {
  sessionId: string;
  topic: string;
  personaId: string;
  status: string;
  planSummary: string;
  hasReport: boolean;
};

type Turn = {
  turnIndex: number;
  stageId: string;
  tutorText: string;
  childText: string;
  createTime: string;
};

type Bubble = {
  id: string;
  role: "child" | "tutor";
  text: string;
  turnIndex?: number;
  audioBase64?: string;
  mimeType?: string;
};

let sharedAudio: HTMLAudioElement | null = null;

function stopVoice() {
  if (sharedAudio) {
    sharedAudio.pause();
    sharedAudio.src = "";
    sharedAudio = null;
  }
}

function playVoice(opts: { text?: string; audioBase64?: string; mimeType?: string }) {
  stopVoice();
  if (opts.audioBase64) {
    const mime = opts.mimeType || "audio/mpeg";
    const audio = new Audio(`data:${mime};base64,${opts.audioBase64}`);
    sharedAudio = audio;
    void audio.play().catch(() => undefined);
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case "PRACTICING":
      return "练习中";
    case "COMPLETED":
      return "已完成";
    case "ABORTED":
      return "已中止";
    case "SAFETY_BLOCKED":
      return "已结束";
    default:
      return status;
  }
}

export default function CetHistoryPage() {
  const params = useParams<{ id: string }>();
  const sessionId = params.id;
  const router = useRouter();
  const [detail, setDetail] = useState<SessionDetail | null>(null);
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [error, setError] = useState("");
  const [busyTurn, setBusyTurn] = useState<number | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    void load();
    return () => {
      mounted.current = false;
      stopVoice();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  async function load() {
    setError("");
    try {
      const [session, turns] = await Promise.all([
        apiJson<SessionDetail>(`/api/cet/sessions/${sessionId}`),
        apiJson<Turn[]>(`/api/cet/sessions/${sessionId}/turns`),
      ]);
      if (!mounted.current) return;
      setDetail(session);
      if (session.planSummary) {
        localStorage.setItem(`cet_plan_${sessionId}`, session.planSummary);
      }
      const next: Bubble[] = [];
      for (const t of turns || []) {
        if (t.childText) {
          next.push({
            id: `c-${t.turnIndex}`,
            role: "child",
            text: t.childText,
            turnIndex: t.turnIndex,
          });
        }
        if (t.tutorText) {
          next.push({
            id: `t-${t.turnIndex}`,
            role: "tutor",
            text: t.tutorText,
            turnIndex: t.turnIndex,
          });
        }
      }
      setBubbles(next);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载失败");
    }
  }

  async function playTutor(b: Bubble) {
    if (b.role !== "tutor" || b.turnIndex == null) return;
    setBusyTurn(b.turnIndex);
    setError("");
    try {
      const tts = await apiJson<TtsPayload>(
        `/api/cet/sessions/${sessionId}/turns/${b.turnIndex}/tts`,
        { method: "POST", body: "{}" },
      );
      if (tts?.audioBase64) {
        setBubbles((prev) =>
          prev.map((x) =>
            x.id === b.id
              ? {
                  ...x,
                  audioBase64: tts.audioBase64,
                  mimeType: tts.mimeType || "audio/mpeg",
                }
              : x,
          ),
        );
        playVoice({
          text: b.text,
          audioBase64: tts.audioBase64,
          mimeType: tts.mimeType,
        });
      } else {
        setError("外教语音合成失败，请稍后重试");
      }
    } catch {
      setError("外教语音合成失败，请稍后重试");
    } finally {
      setBusyTurn(null);
    }
  }

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/cet">
          Kidora · 回看
        </Link>
        <Link className="btn ghost" href="/cet">
          返回
        </Link>
      </div>
      {detail ? (
        <p className="session-plan-hint">
          {detail.topic} · {statusLabel(detail.status)}
          {detail.planSummary ? ` · ${detail.planSummary}` : ""}
        </p>
      ) : null}
      <div className="panel">
        <div className="chat-log">
          {bubbles.length === 0 && !error ? (
            <p className="lead">这节课还没有对话记录。</p>
          ) : null}
          {bubbles.map((b) => (
            <div key={b.id} className={`bubble ${b.role}`}>
              <div className="bubble-body">{b.text}</div>
              {b.role === "tutor" && b.text.trim() ? (
                <button
                  type="button"
                  className="bubble-speak"
                  aria-label="播放外教语音"
                  disabled={busyTurn === b.turnIndex}
                  onClick={() => void playTutor(b)}
                >
                  {busyTurn === b.turnIndex ? "合成中…" : "播放"}
                </button>
              ) : null}
            </div>
          ))}
        </div>
        {error ? <p className="error">{error}</p> : null}
        <div className="history-actions" style={{ marginTop: "1rem" }}>
          {detail?.status === "PRACTICING" ? (
            <Link className="btn" href={`/cet/session/${sessionId}`}>
              继续练习
            </Link>
          ) : null}
          {detail?.hasReport ? (
            <Link className="btn ghost" href={`/cet/report/${sessionId}`}>
              查看报告
            </Link>
          ) : null}
        </div>
      </div>
    </main>
  );
}
