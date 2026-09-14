"use client";

/**
 * 教具素材署名页：展示 attribution_required = TRUE 的道具来源与许可。
 * CC-BY / CC-BY-SA 系列素材必须在此列出，否则不满足协议要求。
 *
 * @author liudy
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, getToken } from "@/lib/api";

type Credit = {
  lemma: string;
  theme: string;
  source: string;
  license: string;
  licenseUrl: string;
  author: string;
  sourceUrl: string;
};

export default function PropCreditsPage() {
  const router = useRouter();
  const [credits, setCredits] = useState<Credit[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    apiJson<Credit[]>("/api/cet/props/credits")
      .then((rows) => setCredits(rows || []))
      .catch((e: Error) => setError(e.message || "加载失败"))
      .finally(() => setLoading(false));
  }, [router]);

  const byLicense = credits.reduce<Record<string, Credit[]>>((acc, credit) => {
    const key = `${credit.source} · ${credit.license}`;
    (acc[key] = acc[key] || []).push(credit);
    return acc;
  }, {});

  return (
    <main className="shell">
      <header className="page-head">
        <h1>教具素材署名</h1>
        <Link href="/home">返回首页</Link>
      </header>

      <p className="muted">
        课堂里的教具图片来自公开素材库。下列素材按其许可协议要求署名；
        未列出的素材为公有领域或 CC0，无需署名。
      </p>

      {loading ? <p>加载中…</p> : null}
      {error ? <p className="error">{error}</p> : null}
      {!loading && !error && credits.length === 0 ? (
        <p className="muted">当前没有需要署名的素材。</p>
      ) : null}

      {Object.entries(byLicense).map(([group, rows]) => (
        <section key={group} className="card">
          <h2>{group}</h2>
          <p className="muted">
            作者：{rows[0].author || "—"}
            {rows[0].licenseUrl ? (
              <>
                {" · "}
                <a href={rows[0].licenseUrl} target="_blank" rel="noreferrer noopener">
                  许可协议原文
                </a>
              </>
            ) : null}
          </p>
          <ul className="credit-list">
            {rows.map((credit) => (
              <li key={credit.lemma}>
                <span className="credit-lemma">{credit.lemma}</span>
                <span className="muted">（{credit.theme}）</span>
                {credit.sourceUrl ? (
                  <a href={credit.sourceUrl} target="_blank" rel="noreferrer noopener">
                    素材页
                  </a>
                ) : null}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  );
}
