"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiJson, getToken, postSse } from "@/lib/api";

type Bubble = { id: string; role: "child" | "tutor" | "system"; text: string };

export default function CetSessionPage() {
  const params = useParams<{ id: string }>();
  const sessionId = params.id;
  const router = useRouter();
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
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
  }, [router, planSummary]);

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || busy) return;
    const childText = text.trim();
    setText("");
    setError("");
    setBusy(true);
    const childId = `c-${Date.now()}`;
    const tutorId = `t-${Date.now()}`;
    setBubbles((prev) => [
      ...prev,
      { id: childId, role: "child", text: childText },
      { id: tutorId, role: "tutor", text: "" },
    ]);
    try {
      await postSse(
        `/api/cet/sessions/${sessionId}/stream`,
        { text: childText },
        {
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
        },
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "发送失败");
    } finally {
      setBusy(false);
    }
  }

  async function complete() {
    setBusy(true);
    setError("");
    try {
      await apiJson(`/api/cet/sessions/${sessionId}/complete`, { method: "POST", body: "{}" });
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
            </div>
          ))}
        </div>
        {error ? <p className="error">{error}</p> : null}
        <form className="composer" onSubmit={send}>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="用英语或中文说说看…"
            disabled={busy}
          />
          <button className="btn" type="submit" disabled={busy || !text.trim()}>
            {busy ? "思考中" : "发送"}
          </button>
        </form>
      </div>
    </main>
  );
}
