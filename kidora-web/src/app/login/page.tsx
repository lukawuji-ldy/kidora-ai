"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, setToken } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("parent1");
  const [password, setPassword] = useState("parent123");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await apiJson<{ token: string }>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(data.token);
      router.push("/home");
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <p className="brand">
        Kidora<span>.</span>
      </p>
      <p className="lead">家长登录后，陪孩子开启 Child English Tutor 文本陪练。</p>
      <form className="panel" onSubmit={onSubmit} style={{ maxWidth: 420 }}>
        <div className="field">
          <label htmlFor="username">用户名</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
          />
        </div>
        <div className="field">
          <label htmlFor="password">密码</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </div>
        {error ? <p className="error">{error}</p> : null}
        <button className="btn" type="submit" disabled={loading}>
          {loading ? "登录中…" : "进入 Kidora"}
        </button>
      </form>
    </main>
  );
}
