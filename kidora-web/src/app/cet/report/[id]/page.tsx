"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiJson, getToken } from "@/lib/api";

export default function CetReportPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [summary, setSummary] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    apiJson<{ childSummary: string }>(`/api/cet/sessions/${params.id}/report`)
      .then((data) => setSummary(data.childSummary || "今天练习很棒！"))
      .catch((err) => setError(err instanceof Error ? err.message : "加载报告失败"));
  }, [params.id, router]);

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/home">
          Kidora
        </Link>
        <Link className="btn ghost" href="/cet">
          再练一次
        </Link>
      </div>
      <h1 className="brand" style={{ fontSize: "2.2rem" }}>
        练习小结
      </h1>
      <p className="lead">给孩子看的鼓励摘要。</p>
      <div className="panel">
        {error ? <p className="error">{error}</p> : <p style={{ fontSize: "1.15rem", lineHeight: 1.6 }}>{summary || "加载中…"}</p>}
      </div>
    </main>
  );
}
