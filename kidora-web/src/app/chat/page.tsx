"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, getToken, postSse } from "@/lib/api";

type Msg = { id: string; role: "user" | "assistant" | "system"; text: string };

export default function ChatPage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<Msg[]>([]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    apiJson<{ sessionId: string }>("/api/chat/sessions", {
      method: "POST",
      body: JSON.stringify({ title: "Kidora Chat" }),
    })
      .then((data) => setSessionId(data.sessionId))
      .catch((err) => setError(err instanceof Error ? err.message : "创建会话失败"));
  }, [router]);

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!sessionId || !text.trim() || busy) return;
    const userText = text.trim();
    setText("");
    setError("");
    setBusy(true);
    const userId = `u-${Date.now()}`;
    const asstId = `a-${Date.now()}`;
    setMessages((prev) => [
      ...prev,
      { id: userId, role: "user", text: userText },
      { id: asstId, role: "assistant", text: "" },
    ]);
    try {
      await postSse(
        `/api/chat/sessions/${sessionId}/stream`,
        { text: userText },
        {
          onDelta: (chunk) => {
            setMessages((prev) =>
              prev.map((m) =>
                m.id === asstId ? { ...m, text: m.text + chunk } : m,
              ),
            );
          },
          onError: (msg) => {
            setError(msg);
            setMessages((prev) =>
              prev.map((m) =>
                m.id === asstId && !m.text
                  ? { ...m, role: "system", text: "暂时无法回答，请稍后再试。" }
                  : m,
              ),
            );
          },
        },
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "发送失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/home">
          Kidora · Chat
        </Link>
        <Link className="btn ghost" href="/home">
          返回
        </Link>
      </div>
      <h1 className="brand" style={{ fontSize: "2.2rem" }}>
        通用助手
      </h1>
      <div className="panel">
        <div className="chat-log">
          {messages.map((m) => (
            <div key={m.id} className={`bubble ${m.role}`}>
              {m.text || (busy && m.role === "assistant" ? "…" : "")}
            </div>
          ))}
        </div>
        {error ? <p className="error">{error}</p> : null}
        <form className="composer" onSubmit={send}>
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="问点什么…"
            disabled={busy || !sessionId}
          />
          <button className="btn" type="submit" disabled={busy || !sessionId || !text.trim()}>
            发送
          </button>
        </form>
      </div>
    </main>
  );
}
