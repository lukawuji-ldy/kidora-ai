"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { apiJson, getToken } from "@/lib/api";
import { PERSONAS } from "@/lib/personas";

type Learner = {
  learnerId: string;
  displayName: string;
  cefrLevel?: string;
  preferredPersona?: string;
};

export default function CetSetupPage() {
  const router = useRouter();
  const [learners, setLearners] = useState<Learner[]>([]);
  const [learnerId, setLearnerId] = useState("");
  const [topic, setTopic] = useState("Animals and pets");
  const [personaId, setPersonaId] = useState("emma");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

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
      .catch((err) => setError(err instanceof Error ? err.message : "加载学习者失败"));
  }, [router]);

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
    </main>
  );
}
