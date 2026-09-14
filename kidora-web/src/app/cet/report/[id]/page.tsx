"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { apiJson, getToken } from "@/lib/api";

type AssessmentView = {
  grammar?: number;
  vocabulary?: number;
  fluency?: number;
  problems?: string[];
  focus?: string[];
  pauseNewVocab?: boolean;
  encouragement?: string;
  childSummary?: string;
};

type ReportPayload = {
  childSummary?: string;
  parentSummary?: string;
  assessment?: string | AssessmentView;
  topic?: string;
  learnerName?: string;
  cefrLevel?: string;
  completedAt?: string;
};

function parseAssessment(raw: string | AssessmentView | undefined): AssessmentView {
  if (!raw) return {};
  if (typeof raw === "object") return raw;
  try {
    return JSON.parse(raw) as AssessmentView;
  } catch {
    return {};
  }
}

function formatCompletedAt(iso: string | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  const m = d.getMonth() + 1;
  const day = d.getDate();
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${m}/${day} ${hh}:${mm}`;
}

function scoreOrDash(n: number | undefined): string {
  return typeof n === "number" && Number.isFinite(n) ? String(n) : "—";
}

export default function CetReportPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [report, setReport] = useState<ReportPayload | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    apiJson<ReportPayload>(`/api/cet/sessions/${params.id}/report`)
      .then((data) => setReport(data))
      .catch((err) => setError(err instanceof Error ? err.message : "加载报告失败"));
  }, [params.id, router]);

  const assessment = useMemo(() => parseAssessment(report?.assessment), [report]);
  const problems = (assessment.problems || []).filter((p) => p && p.trim());
  const focus = (assessment.focus || []).filter((f) => f && f.trim());
  const parentSummary =
    (report?.parentSummary && report.parentSummary.trim()) ||
    "本节练习已完成。分数为 AI 参考，非正式测评。";
  const childLine =
    (assessment.encouragement && assessment.encouragement.trim()) ||
    (report?.childSummary && report.childSummary.trim()) ||
    (assessment.childSummary && assessment.childSummary.trim()) ||
    "";

  return (
    <main className="shell">
      <div className="topbar">
        <Link className="mini-brand" href="/home">
          Kidora
        </Link>
        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <Link className="btn ghost" href="/home">
            回首页
          </Link>
          <Link className="btn ghost" href="/cet">
            上课记录
          </Link>
        </div>
      </div>
      <h1 className="brand" style={{ fontSize: "2.2rem" }}>
        学习报告
      </h1>
      <p className="lead">给家长看的本节练习说明。</p>

      {error ? (
        <div className="panel">
          <p className="error">{error}</p>
        </div>
      ) : !report ? (
        <div className="panel">
          <p>加载中…</p>
        </div>
      ) : (
        <>
          <section className="panel report-meta">
            <p>
              <strong>{report.topic || "练习"}</strong>
            </p>
            <p className="lead" style={{ margin: 0 }}>
              {[report.learnerName, report.cefrLevel, formatCompletedAt(report.completedAt)]
                .filter(Boolean)
                .join(" · ") || "已完成"}
            </p>
          </section>

          <section className="panel">
            <h2 className="report-section-title">本节总结</h2>
            <p className="report-body">{parentSummary}</p>
          </section>

          <section className="panel">
            <h2 className="report-section-title">能力参考分</h2>
            <p className="lead" style={{ marginTop: 0 }}>
              AI 参考，非正式测评
            </p>
            <div className="report-scores">
              <div>
                <span>语法</span>
                <strong>{scoreOrDash(assessment.grammar)}</strong>
              </div>
              <div>
                <span>词汇</span>
                <strong>{scoreOrDash(assessment.vocabulary)}</strong>
              </div>
              <div>
                <span>流利度</span>
                <strong>{scoreOrDash(assessment.fluency)}</strong>
              </div>
            </div>
          </section>

          <section className="panel">
            <h2 className="report-section-title">错例与纠正</h2>
            {problems.length === 0 ? (
              <p className="lead" style={{ margin: 0 }}>
                本节未记录明显错句。
              </p>
            ) : (
              <ul className="report-list">
                {problems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2 className="report-section-title">建议重点</h2>
            {focus.length === 0 && !assessment.pauseNewVocab ? (
              <p className="lead" style={{ margin: 0 }}>
                继续按当前主题巩固即可。
              </p>
            ) : (
              <ul className="report-list">
                {focus.map((f) => (
                  <li key={f}>{f}</li>
                ))}
                {assessment.pauseNewVocab ? <li>建议暂缓引入新词，先巩固已学句型</li> : null}
              </ul>
            )}
          </section>

          {childLine ? (
            <section className="panel report-child">
              <h2 className="report-section-title">给孩子的一句话</h2>
              <p className="report-body">{childLine}</p>
            </section>
          ) : null}
        </>
      )}
    </main>
  );
}
