"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { apiJson, setToken } from "@/lib/api";

const LEVELS = [
  { value: "BEGINNER", label: "启蒙" },
  { value: "ELEMENTARY", label: "初级" },
  { value: "INTERMEDIATE", label: "中级" },
  { value: "ADVANCED", label: "进阶" },
] as const;

export default function RegisterPage() {
  const router = useRouter();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [childNickname, setChildNickname] = useState("");
  const [englishLevel, setEnglishLevel] = useState<string>("ELEMENTARY");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await apiJson<{ token: string }>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username,
          password,
          childNickname,
          englishLevel,
        }),
      });
      setToken(data.token);
      router.push("/home");
    } catch (err) {
      setError(err instanceof Error ? err.message : "注册失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <p className="brand">
        Kidora<span>.</span>
      </p>
      <p className="lead">注册家长账号，并为孩子填写昵称与英语水平，即可开始陪练。</p>
      <form className="panel" onSubmit={onSubmit} style={{ maxWidth: 420 }}>
        <div className="field">
          <label htmlFor="username">用户名</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
            minLength={3}
            maxLength={64}
          />
        </div>
        <div className="field">
          <label htmlFor="password">密码</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            required
            minLength={6}
          />
        </div>
        <div className="field">
          <label htmlFor="childNickname">儿童昵称</label>
          <input
            id="childNickname"
            value={childNickname}
            onChange={(e) => setChildNickname(e.target.value)}
            autoComplete="nickname"
            required
            maxLength={32}
            placeholder="外教会这样称呼孩子"
          />
        </div>
        <div className="field">
          <label htmlFor="englishLevel">英语水平</label>
          <select
            id="englishLevel"
            value={englishLevel}
            onChange={(e) => setEnglishLevel(e.target.value)}
            required
          >
            {LEVELS.map((lv) => (
              <option key={lv.value} value={lv.value}>
                {lv.label}
              </option>
            ))}
          </select>
        </div>
        <p className="hint" style={{ margin: "0 0 0.75rem", opacity: 0.75, fontSize: "0.9rem" }}>
          英语水平决定外教对话难度，不是周岁年龄。
        </p>
        {error ? <p className="error">{error}</p> : null}
        <button className="btn" type="submit" disabled={loading}>
          {loading ? "注册中…" : "注册并进入"}
        </button>
        <p style={{ marginTop: "1rem", fontSize: "0.9rem" }}>
          已有账号？{" "}
          <Link href="/login" style={{ textDecoration: "underline" }}>
            去登录
          </Link>
        </p>
      </form>
    </main>
  );
}
