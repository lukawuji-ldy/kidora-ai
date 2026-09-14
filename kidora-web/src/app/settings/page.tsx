"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, clearToken, getToken, setToken } from "@/lib/api";

const LEVELS = [
  { value: "BEGINNER", label: "启蒙" },
  { value: "ELEMENTARY", label: "初级" },
  { value: "INTERMEDIATE", label: "中级" },
  { value: "ADVANCED", label: "进阶" },
] as const;

type Profile = {
  userId: string;
  username: string;
  nickname: string;
  role: string;
};

type Learner = {
  learnerId: string;
  displayName: string;
  cefrLevel?: string;
  englishLevel?: string;
  preferredPersona?: string;
};

function levelLabel(value?: string): string {
  return LEVELS.find((l) => l.value === value)?.label || value || "—";
}

export default function SettingsPage() {
  const router = useRouter();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [nickname, setNickname] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [learners, setLearners] = useState<Learner[]>([]);
  const [newChildName, setNewChildName] = useState("");
  const [newChildLevel, setNewChildLevel] = useState("ELEMENTARY");
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editLevel, setEditLevel] = useState("ELEMENTARY");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const loadAll = useCallback(async () => {
    const [me, list] = await Promise.all([
      apiJson<Profile>("/api/auth/me"),
      apiJson<Learner[]>("/api/learners"),
    ]);
    setProfile(me);
    setNickname(me.nickname || "");
    setLearners(list || []);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    loadAll().catch((err) => {
      setError(err instanceof Error ? err.message : "加载失败");
    });
  }, [router, loadAll]);

  async function saveNickname(e: FormEvent) {
    e.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const data = await apiJson<{ token: string; nickname: string }>("/api/auth/me", {
        method: "PATCH",
        body: JSON.stringify({ nickname }),
      });
      if (data.token) setToken(data.token);
      setProfile((p) => (p ? { ...p, nickname: data.nickname } : p));
      setNotice("昵称已保存");
    } catch (err) {
      setError(err instanceof Error ? err.message : "保存昵称失败");
    } finally {
      setBusy(false);
    }
  }

  async function changePassword(e: FormEvent) {
    e.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiJson<{ ok: boolean }>("/api/auth/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      setCurrentPassword("");
      setNewPassword("");
      setNotice("密码已更新");
    } catch (err) {
      setError(err instanceof Error ? err.message : "修改密码失败");
    } finally {
      setBusy(false);
    }
  }

  async function addChild(e: FormEvent) {
    e.preventDefault();
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiJson<Learner>("/api/learners", {
        method: "POST",
        body: JSON.stringify({
          displayName: newChildName,
          englishLevel: newChildLevel,
        }),
      });
      setNewChildName("");
      setNewChildLevel("ELEMENTARY");
      await loadAll();
      setNotice("已添加儿童");
    } catch (err) {
      setError(err instanceof Error ? err.message : "添加失败");
    } finally {
      setBusy(false);
    }
  }

  function startEdit(child: Learner) {
    setEditingId(child.learnerId);
    setEditName(child.displayName);
    setEditLevel(child.englishLevel || "ELEMENTARY");
    setError("");
    setNotice("");
  }

  async function saveChild(e: FormEvent) {
    e.preventDefault();
    if (!editingId) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiJson<Learner>(`/api/learners/${encodeURIComponent(editingId)}`, {
        method: "PATCH",
        body: JSON.stringify({
          displayName: editName,
          englishLevel: editLevel,
        }),
      });
      setEditingId(null);
      await loadAll();
      setNotice("儿童资料已更新");
    } catch (err) {
      setError(err instanceof Error ? err.message : "更新失败");
    } finally {
      setBusy(false);
    }
  }

  async function removeChild(child: Learner) {
    if (!window.confirm(`确定停用「${child.displayName}」？停用后开课列表将不再显示。`)) {
      return;
    }
    setError("");
    setNotice("");
    setBusy(true);
    try {
      await apiJson<{ ok: boolean }>(`/api/learners/${encodeURIComponent(child.learnerId)}`, {
        method: "DELETE",
      });
      if (editingId === child.learnerId) setEditingId(null);
      await loadAll();
      setNotice("已停用该儿童档案");
    } catch (err) {
      setError(err instanceof Error ? err.message : "停用失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <div className="topbar">
        <p className="mini-brand">Kidora</p>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
          <Link className="btn ghost" href="/home">
            返回首页
          </Link>
          <button
            className="btn ghost"
            type="button"
            onClick={() => {
              clearToken();
              router.push("/login");
            }}
          >
            退出
          </button>
        </div>
      </div>

      <h1 className="brand" style={{ fontSize: "2.2rem" }}>
        个人中心<span>.</span>
      </h1>
      <p className="lead">管理登录资料与儿童档案。</p>

      {error ? <p className="error">{error}</p> : null}
      {notice ? <p className="hint" style={{ color: "var(--sea)" }}>{notice}</p> : null}

      <section className="panel" style={{ maxWidth: 520, marginBottom: "1.25rem" }}>
        <h2 style={{ fontSize: "1.2rem", marginTop: 0 }}>家长资料</h2>
        <form onSubmit={saveNickname}>
          <div className="field">
            <label htmlFor="username">用户名</label>
            <input id="username" value={profile?.username || ""} readOnly disabled />
          </div>
          <div className="field">
            <label htmlFor="nickname">展示昵称</label>
            <input
              id="nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
              maxLength={32}
            />
          </div>
          <button className="btn" type="submit" disabled={busy}>
            保存昵称
          </button>
        </form>

        <hr style={{ margin: "1.25rem 0", border: 0, borderTop: "1px solid rgba(0,0,0,0.08)" }} />

        <form onSubmit={changePassword}>
          <div className="field">
            <label htmlFor="currentPassword">当前密码</label>
            <input
              id="currentPassword"
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              autoComplete="current-password"
              required
              minLength={6}
            />
          </div>
          <div className="field">
            <label htmlFor="newPassword">新密码</label>
            <input
              id="newPassword"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              required
              minLength={6}
            />
          </div>
          <button className="btn" type="submit" disabled={busy}>
            修改密码
          </button>
        </form>
      </section>

      <section className="panel" style={{ maxWidth: 520 }}>
        <h2 style={{ fontSize: "1.2rem", marginTop: 0 }}>儿童档案</h2>
        <ul className="history-list" style={{ marginBottom: "1rem" }}>
          {learners.map((child) => (
            <li key={child.learnerId}>
              {editingId === child.learnerId ? (
                <form onSubmit={saveChild}>
                  <div className="field">
                    <label htmlFor={`edit-name-${child.learnerId}`}>昵称</label>
                    <input
                      id={`edit-name-${child.learnerId}`}
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      required
                      maxLength={32}
                    />
                  </div>
                  <div className="field">
                    <label htmlFor={`edit-level-${child.learnerId}`}>英语水平</label>
                    <select
                      id={`edit-level-${child.learnerId}`}
                      value={editLevel}
                      onChange={(e) => setEditLevel(e.target.value)}
                      required
                    >
                      {LEVELS.map((lv) => (
                        <option key={lv.value} value={lv.value}>
                          {lv.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
                    <button className="btn" type="submit" disabled={busy}>
                      保存
                    </button>
                    <button
                      className="btn ghost"
                      type="button"
                      disabled={busy}
                      onClick={() => setEditingId(null)}
                    >
                      取消
                    </button>
                  </div>
                </form>
              ) : (
                <div className="history-header" style={{ marginBottom: 0 }}>
                  <div>
                    <strong>{child.displayName}</strong>
                    <p className="hint" style={{ margin: "0.2rem 0 0" }}>
                      {levelLabel(child.englishLevel)}
                      {child.cefrLevel ? ` · ${child.cefrLevel}` : ""}
                    </p>
                  </div>
                  <div style={{ display: "flex", gap: "0.4rem" }}>
                    <button
                      className="btn ghost"
                      type="button"
                      disabled={busy}
                      onClick={() => startEdit(child)}
                    >
                      编辑
                    </button>
                    <button
                      className="btn ghost"
                      type="button"
                      disabled={busy || learners.length <= 1}
                      onClick={() => void removeChild(child)}
                      title={learners.length <= 1 ? "至少保留一名儿童" : undefined}
                    >
                      停用
                    </button>
                  </div>
                </div>
              )}
            </li>
          ))}
        </ul>

        <h3 style={{ fontSize: "1rem" }}>添加儿童</h3>
        <form onSubmit={addChild}>
          <div className="field">
            <label htmlFor="newChildName">昵称</label>
            <input
              id="newChildName"
              value={newChildName}
              onChange={(e) => setNewChildName(e.target.value)}
              required
              maxLength={32}
              placeholder="外教会这样称呼孩子"
            />
          </div>
          <div className="field">
            <label htmlFor="newChildLevel">英语水平</label>
            <select
              id="newChildLevel"
              value={newChildLevel}
              onChange={(e) => setNewChildLevel(e.target.value)}
              required
            >
              {LEVELS.map((lv) => (
                <option key={lv.value} value={lv.value}>
                  {lv.label}
                </option>
              ))}
            </select>
          </div>
          <button className="btn" type="submit" disabled={busy}>
            添加
          </button>
        </form>
      </section>
    </main>
  );
}
