"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, getToken } from "@/lib/api";
import { PERSONAS, personaStillUrl } from "@/lib/personas";

type Learner = {
  learnerId: string;
  displayName: string;
  cefrLevel?: string;
  preferredPersona?: string;
};

type SessionItem = {
  sessionId: string;
  learnerId: string;
  topic: string;
  personaId: string;
  cefrLevel: string;
  status: string;
  createTime: string;
  hasReport: boolean;
};

function statusLabel(status: string): string {
  switch (status) {
    case "PRACTICING":
      return "练习中";
    case "EVALUATING":
    case "REPLANNING":
      return "评测中";
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

function personaName(id: string): string {
  return PERSONAS.find((p) => p.id === id)?.name || id || "外教";
}

function formatTime(iso: string): string {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString("zh-CN", {
      month: "numeric",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return iso;
  }
}

function confirmSingleDelete(status: string): boolean {
  if (status === "PRACTICING") {
    return window.confirm(
      "该课仍在练习中，删除后无法继续，且记录不可恢复。确定删除？",
    );
  }
  return true;
}

function confirmBatchDelete(selected: SessionItem[]): boolean {
  const n = selected.length;
  const hasPracticing = selected.some((s) => s.status === "PRACTICING");
  let msg = `将永久删除 ${n} 条上课记录，不可恢复。确定？`;
  if (hasPracticing) {
    msg += "其中含练习中的课程，删除后无法继续。";
  }
  return window.confirm(msg);
}

export default function CetSetupPage() {
  const router = useRouter();
  const [learners, setLearners] = useState<Learner[]>([]);
  const [learnerId, setLearnerId] = useState("");
  const [topic, setTopic] = useState("Animals and pets");
  const [personaId, setPersonaId] = useState("emma");
  const [sessions, setSessions] = useState<SessionItem[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [managing, setManaging] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [deleting, setDeleting] = useState(false);

  const loadHistory = useCallback(async (lid: string) => {
    if (!lid) {
      setSessions([]);
      return;
    }
    setHistoryLoading(true);
    try {
      const list = await apiJson<SessionItem[]>(
        `/api/cet/sessions?learnerId=${encodeURIComponent(lid)}`,
      );
      setSessions(list || []);
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载历史失败");
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    apiJson<Learner[]>("/api/learners")
      .then((list) => {
        setLearners(list || []);
        if (list?.length) {
          setLearnerId(list[0].learnerId);
          if (list[0].preferredPersona) setPersonaId(list[0].preferredPersona);
        }
      })
      .catch((err) => {
        const msg = err instanceof Error ? err.message : "加载学习者失败";
        if (/未登录|令牌/.test(msg)) {
          router.replace("/login");
          return;
        }
        setError(msg);
      });
  }, [router]);

  useEffect(() => {
    if (learnerId) void loadHistory(learnerId);
  }, [learnerId, loadHistory]);

  useEffect(() => {
    setManaging(false);
    setSelectedIds(new Set());
  }, [learnerId]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await apiJson<{ sessionId: string; planSummary?: string }>(
        "/api/cet/sessions",
        {
          method: "POST",
          body: JSON.stringify({ learnerId, topic, personaId }),
        },
      );
      localStorage.setItem(`cet_plan_${data.sessionId}`, data.planSummary || "");
      router.push(`/cet/session/${data.sessionId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "开课失败");
    } finally {
      setLoading(false);
    }
  }

  function exitManage() {
    setManaging(false);
    setSelectedIds(new Set());
  }

  function toggleSelect(sessionId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(sessionId)) next.delete(sessionId);
      else next.add(sessionId);
      return next;
    });
  }

  function toggleSelectAll() {
    if (selectedIds.size === sessions.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(sessions.map((s) => s.sessionId)));
    }
  }

  async function deleteOne(session: SessionItem) {
    if (!confirmSingleDelete(session.status)) return;
    setError("");
    setDeleting(true);
    try {
      await apiJson<{ deletedCount: number }>(
        `/api/cet/sessions/${encodeURIComponent(session.sessionId)}`,
        { method: "DELETE" },
      );
      setSessions((prev) => prev.filter((s) => s.sessionId !== session.sessionId));
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(session.sessionId);
        return next;
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败，请重试");
    } finally {
      setDeleting(false);
    }
  }

  async function deleteSelected() {
    const selected = sessions.filter((s) => selectedIds.has(s.sessionId));
    if (selected.length === 0) return;
    if (!confirmBatchDelete(selected)) return;
    setError("");
    setDeleting(true);
    try {
      await apiJson<{ deletedCount: number }>("/api/cet/sessions/batch-delete", {
        method: "POST",
        body: JSON.stringify({ sessionIds: selected.map((s) => s.sessionId) }),
      });
      const removed = new Set(selected.map((s) => s.sessionId));
      setSessions((prev) => prev.filter((s) => !removed.has(s.sessionId)));
      setSelectedIds(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : "删除失败，请重试");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/home">
          Kidora
        </Link>
        <Link className="btn ghost" href="/home">
          返回
        </Link>
      </div>
      <h1 className="brand" style={{ fontSize: "2.4rem" }}>
        Child English Tutor
      </h1>
      <p className="lead">选学习者、主题与外教人设，开始文本陪练。</p>
      <form className="panel" onSubmit={onSubmit}>
        <div className="field">
          <label htmlFor="learner">学习者</label>
          <select
            id="learner"
            value={learnerId}
            onChange={(e) => setLearnerId(e.target.value)}
            required
          >
            {learners.map((l) => (
              <option key={l.learnerId} value={l.learnerId}>
                {l.displayName} ({l.cefrLevel || "A1"})
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="topic">练习主题</label>
          <input
            id="topic"
            value={topic}
            onChange={(e) => setTopic(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label>外教人设</label>
          <div className="persona-grid">
            {PERSONAS.map((p) => (
              <button
                key={p.id}
                type="button"
                className={`persona ${personaId === p.id ? "selected" : ""}`}
                onClick={() => setPersonaId(p.id)}
              >
                <span
                  className="persona-pick-avatar"
                  style={{ background: p.color }}
                  aria-hidden="true"
                >
                  <span className="persona-pick-initial">{p.initial}</span>
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img
                    className="persona-pick-media"
                    src={personaStillUrl(p.id, "idle")}
                    alt=""
                    onError={(e) => {
                      (e.currentTarget as HTMLImageElement).style.display = "none";
                    }}
                  />
                </span>
                <strong>{p.name}</strong>
                <small>{p.tagline}</small>
              </button>
            ))}
          </div>
        </div>
        {error ? <p className="error">{error}</p> : null}
        <button className="btn" type="submit" disabled={loading || !learnerId}>
          {loading ? "开课中…" : "开始练习"}
        </button>
      </form>

      <section className="panel" style={{ marginTop: "1.5rem" }}>
        <div className="history-header">
          <h2 style={{ fontSize: "1.25rem", margin: 0 }}>上课记录</h2>
          {sessions.length > 0 ? (
            managing ? (
              <div className="history-manage-bar">
                <span className="history-selected-count">已选 {selectedIds.size} 条</span>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={toggleSelectAll}
                  disabled={deleting}
                >
                  {selectedIds.size === sessions.length ? "取消全选" : "全选"}
                </button>
                <button
                  type="button"
                  className="btn ghost"
                  disabled={deleting || selectedIds.size === 0}
                  onClick={() => void deleteSelected()}
                >
                  {deleting ? "删除中…" : "删除所选"}
                </button>
                <button
                  type="button"
                  className="btn ghost"
                  onClick={exitManage}
                  disabled={deleting}
                >
                  完成
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="btn ghost"
                onClick={() => setManaging(true)}
              >
                管理
              </button>
            )
          ) : null}
        </div>
        {historyLoading ? <p className="lead">加载中…</p> : null}
        {!historyLoading && sessions.length === 0 ? (
          <p className="lead">还没有上课记录。</p>
        ) : null}
        <ul className="history-list">
          {sessions.map((s) => {
            const resumable = s.status === "PRACTICING";
            const checked = selectedIds.has(s.sessionId);
            return (
              <li key={s.sessionId} className="history-item">
                {managing ? (
                  <label className="history-check">
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={deleting}
                      onChange={() => toggleSelect(s.sessionId)}
                      aria-label={`选择 ${s.topic || "练习"}`}
                    />
                  </label>
                ) : null}
                <div className="history-meta">
                  <strong>{s.topic || "练习"}</strong>
                  <span>
                    {personaName(s.personaId)} · {statusLabel(s.status)} ·{" "}
                    {formatTime(s.createTime)}
                  </span>
                </div>
                <div className="history-actions">
                  {!managing ? (
                    <>
                      {resumable ? (
                        <Link className="btn" href={`/cet/session/${s.sessionId}`}>
                          继续练习
                        </Link>
                      ) : null}
                      <Link className="btn ghost" href={`/cet/history/${s.sessionId}`}>
                        回看
                      </Link>
                      {s.hasReport ? (
                        <Link className="btn ghost" href={`/cet/report/${s.sessionId}`}>
                          报告
                        </Link>
                      ) : null}
                      <button
                        type="button"
                        className="btn ghost"
                        disabled={deleting}
                        onClick={() => void deleteOne(s)}
                      >
                        删除
                      </button>
                    </>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      </section>
    </main>
  );
}
