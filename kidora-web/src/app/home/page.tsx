"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { clearToken, getToken } from "@/lib/api";

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  return (
    <main className="shell">
      <div className="topbar">
        <p className="mini-brand">Kidora</p>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
          <Link className="btn ghost" href="/settings">
            个人中心
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
      <h1 className="brand">
        Kidora<span>.</span>
      </h1>
      <p className="lead">选一个入口：儿童英语口语陪练，或通用家庭助手。</p>
      <div className="grid-2">
        <Link className="entry-tile" href="/cet">
          <h2>Child English Tutor</h2>
          <p>开课、文本对话练习、结课鼓励摘要。</p>
        </Link>
        <Link className="entry-tile" href="/chat" style={{ animationDelay: "0.08s" }}>
          <h2>通用助手</h2>
          <p>家长侧短对话，流式回复。</p>
        </Link>
      </div>
    </main>
  );
}
