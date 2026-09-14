"use client";

import Link from "next/link";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  apiJson,
  getToken,
  postClientTiming,
  postSse,
  prefetchAuthedBlob,
  PropStagePayload,
  TtsPayload,
} from "@/lib/api";
import { PersonaStage } from "@/components/cet/PersonaStage";
import {
  goalStepFromTurnCount,
  PERSONA_FOCUS_STAGE,
  toPropStageView,
  type PropAsset,
  type PropStageView,
} from "@/lib/lessonProps";
import { themeFromTopic } from "@/lib/lessonThemes";
import { inferSpeakingEmotion, SpeakingEmotion } from "@/lib/personaEmotions";
import {
  AUTO_LISTEN_DEBOUNCE_MS,
  AUTO_LISTEN_RETRY_MS,
  shouldArmAutoListen,
} from "@/lib/tutorVoiceGate";
import {
  isTutorVoicePlaying,
  playTutorVoice,
  speakableText,
  stopTutorVoice,
} from "@/lib/tutorVoice";
import { RECORDER_ALREADY_ENDED } from "@/lib/vadGate";
import { WavRecorder } from "@/lib/wavRecorder";

const AUTO_LISTEN_KEY = "cet.autoListen";

/** 开课时预热本课可能用到的道具图，之后 turn.prop 切换是瞬时的。 */
function prefetchPropImages(assets: PropAsset[] | undefined): void {
  for (const asset of (assets || []).slice(0, 5)) {
    if (asset?.url) void prefetchAuthedBlob(asset.url);
  }
}

function readAutoListenPrefer(): boolean {
  if (typeof window === "undefined") return true;
  const v = localStorage.getItem(AUTO_LISTEN_KEY);
  if (v == null) return true;
  return v !== "0" && v !== "false";
}

type CallState = "idle" | "listening" | "thinking" | "speaking" | "blocked";
type EmotionFx = "nod" | SpeakingEmotion;

type Bubble = {
  id: string;
  role: "child" | "tutor" | "system";
  text: string;
  turnIndex?: number;
  audioBase64?: string;
  mimeType?: string;
  /** 本场浏览器内存中有孩子原音（不落库）；巨大 base64 放 ref，避免撑爆渲染 */
  hasLocalAudio?: boolean;
  /** Autoplay blocked — highlight replay */
  needsTap?: boolean;
};

type SessionDetail = {
  sessionId: string;
  status: string;
  planSummary: string;
  hasReport: boolean;
  personaId?: string;
  topic?: string;
  childGoals?: string[];
  vocabHints?: string[];
  propAssets?: PropAsset[];
  wrapUpPhase?: string | null;
};

type Turn = {
  turnIndex: number;
  stageId: string;
  tutorText: string;
  childText: string;
};

function isTerminalStatus(status: string): boolean {
  return status === "COMPLETED" || status === "ABORTED" || status === "SAFETY_BLOCKED";
}

function isBusyEvalStatus(status: string): boolean {
  return status === "EVALUATING" || status === "REPLANNING";
}

function turnsToBubbles(turns: Turn[]): Bubble[] {
  const next: Bubble[] = [];
  for (const t of turns || []) {
    if (t.childText) {
      next.push({
        id: `c-${t.turnIndex}`,
        role: "child",
        text: t.childText,
        turnIndex: t.turnIndex,
      });
    }
    if (t.tutorText) {
      next.push({
        id: `t-${t.turnIndex}`,
        role: "tutor",
        text: t.tutorText,
        turnIndex: t.turnIndex,
      });
    }
  }
  return next;
}

function statusLabel(state: CallState): string {
  switch (state) {
    case "listening":
      return "在听你说";
    case "thinking":
      return "想一想…";
    case "speaking":
      return "外教在说";
    case "blocked":
      return "我们换个话题吧";
    default:
      return "轮到你说";
  }
}

function countRounds(bubbles: Bubble[]): number {
  return bubbles.filter((b) => b.role === "tutor" && b.text.trim()).length;
}

export default function CetSessionPage() {
  const params = useParams<{ id: string }>();
  const sessionId = params.id;
  const router = useRouter();
  const [bubbles, setBubbles] = useState<Bubble[]>([]);
  const [text, setText] = useState("");
  const [callState, setCallState] = useState<CallState>("idle");
  const [recording, setRecording] = useState(false);
  const [showTextInput, setShowTextInput] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [captionExpanded, setCaptionExpanded] = useState(false);
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const [readOnlyHint, setReadOnlyHint] = useState("");
  const [personaId, setPersonaId] = useState("emma");
  const [topic, setTopic] = useState("");
  const [emotion, setEmotion] = useState<EmotionFx | "neutral">("neutral");
  const [nodding, setNodding] = useState(false);
  const recorderRef = useRef<WavRecorder | null>(null);
  const bootStarted = useRef(false);
  const bubblesRef = useRef<Bubble[]>([]);
  const childAudioRef = useRef(
    new Map<string, { audioBase64: string; mimeType: string }>(),
  );
  const voiceSubmitClaimed = useRef(false);
  const [planSummary, setPlanSummary] = useState("");
  const [childGoals, setChildGoals] = useState<string[]>([]);
  const [propStage, setPropStage] = useState<PropStageView>(PERSONA_FOCUS_STAGE);
  const [autoListen, setAutoListen] = useState(true);
  const [completing, setCompleting] = useState(false);
  const [wrapUpHint, setWrapUpHint] = useState(false);
  const pendingAutoReportRef = useRef(false);
  const farewellTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const queueWrapUpStep1Ref = useRef(false);
  const tStopRef = useRef<number | null>(null);
  const reportedTurnRef = useRef<number | null>(null);
  const emotionClearRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const autoListenRef = useRef(true);
  const autoListenGen = useRef(0);

  const busy =
    callState === "listening" ||
    callState === "thinking" ||
    callState === "speaking";
  const themeId = themeFromTopic(topic, planSummary);
  const captionTutor = [...bubbles].reverse().find((b) => b.role === "tutor");
  const captionChild = [...bubbles].reverse().find((b) => b.role === "child");
  const rounds = countRounds(bubbles);
  // blocked 时强制收起分屏；其余一律听后端 turn.prop 事件。
  const effectivePropStage = callState === "blocked" ? PERSONA_FOCUS_STAGE : propStage;
  const goalStep = goalStepFromTurnCount(rounds);
  const tutorCaptionText =
    captionTutor?.text ||
    (callState === "thinking" || callState === "speaking" ? "…" : "—");
  const tutorCaptionLong = (captionTutor?.text?.trim().length ?? 0) > 48;
  const sticker =
    emotion === "smile" || emotion === "clap" ? emotion : null;

  useEffect(() => {
    autoListenRef.current = autoListen;
  }, [autoListen]);

  useEffect(() => {
    setAutoListen(readAutoListenPrefer());
  }, []);

  useEffect(() => {
    setCaptionExpanded(false);
  }, [captionTutor?.id, captionTutor?.text]);

  useEffect(() => {
    bubblesRef.current = bubbles;
  }, [bubbles]);

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    return () => {
      recorderRef.current?.cancel();
      stopTutorVoice();
      if (emotionClearRef.current) clearTimeout(emotionClearRef.current);
    };
  }, [router]);

  useEffect(() => {
    return () => clearFarewellTimeout();
  }, []);

  useEffect(() => {
    if (!getToken() || !sessionId || bootStarted.current) return;
    bootStarted.current = true;
    void bootstrapSession();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId]);

  function triggerNod() {
    setNodding(true);
    setEmotion("nod");
    window.setTimeout(() => {
      setNodding(false);
      setEmotion((e) => (e === "nod" ? "neutral" : e));
    }, 400);
  }

  function showSpeakingEmotion(tutorText: string) {
    const inferred = inferSpeakingEmotion(tutorText);
    if (inferred === "neutral") {
      setEmotion("neutral");
      return;
    }
    setEmotion(inferred);
    if (emotionClearRef.current) clearTimeout(emotionClearRef.current);
    emotionClearRef.current = setTimeout(() => {
      setEmotion("neutral");
    }, 1500);
  }

  function reportE2eIfNeeded(turnIndex?: number) {
    if (tStopRef.current == null) return;
    const heard = performance.now();
    const e2e = Math.round(heard - tStopRef.current);
    tStopRef.current = null;
    const idx = turnIndex ?? reportedTurnRef.current;
    if (idx == null || idx < 1 || e2e < 0) return;
    void postClientTiming(sessionId, idx, e2e);
  }

  function clearFarewellTimeout() {
    if (farewellTimeoutRef.current) {
      clearTimeout(farewellTimeoutRef.current);
      farewellTimeoutRef.current = null;
    }
  }

  function maybeGoReportAfterSpeech() {
    if (pendingAutoReportRef.current) {
      pendingAutoReportRef.current = false;
      router.push(`/cet/report/${sessionId}`);
    }
  }

  function markAutoCompletePending() {
    pendingAutoReportRef.current = true;
    if (!isTutorVoicePlaying()) {
      maybeGoReportAfterSpeech();
    }
  }

  function startFarewellTimeout() {
    clearFarewellTimeout();
    farewellTimeoutRef.current = setTimeout(() => {
      void runWrapUpTimeout();
    }, 45_000);
  }

  async function bootstrapSession() {
    setError("");
    setCallState("thinking");
    try {
      const cached =
        typeof window !== "undefined"
          ? localStorage.getItem(`cet_plan_${sessionId}`) || ""
          : "";
      const [session, turns] = await Promise.all([
        apiJson<SessionDetail>(`/api/cet/sessions/${sessionId}`),
        apiJson<Turn[]>(`/api/cet/sessions/${sessionId}/turns`),
      ]);
      if (session.personaId) setPersonaId(session.personaId);
      if (session.topic) setTopic(session.topic);
      const summary = session.planSummary || cached;
      if (summary) {
        localStorage.setItem(`cet_plan_${sessionId}`, summary);
        setPlanSummary(summary);
      }
      if (session.childGoals?.length) setChildGoals(session.childGoals.slice(0, 3));
      // 开课预取只做预热（提前触发浏览器缓存），舞台态一律等 turn.prop 事件。
      prefetchPropImages(session.propAssets);
      if (isTerminalStatus(session.status)) {
        if (session.hasReport || session.status === "COMPLETED") {
          router.replace(`/cet/report/${sessionId}`);
        } else {
          router.replace(`/cet/history/${sessionId}`);
        }
        return;
      }
      if (isBusyEvalStatus(session.status)) {
        setBubbles(turnsToBubbles(turns || []));
        setReadOnlyHint("课程正在评测中，请稍后再试或稍候刷新。");
        setReady(true);
        setCallState("idle");
        return;
      }
      if ((turns || []).length > 0) {
        setBubbles(turnsToBubbles(turns));
        setReady(true);
        setCallState("idle");
        const phase = session.wrapUpPhase || "";
        if (phase === "pending_tutor_farewell") {
          void runWrapUpStream();
          return;
        }
        if (phase === "await_child_farewell") {
          setWrapUpHint(true);
          startFarewellTimeout();
          return;
        }
        return;
      }
      setReady(true);
      await runOpening();
    } catch (err) {
      setError(err instanceof Error ? err.message : "加载会话失败");
      setReady(true);
      setCallState("idle");
    }
  }

  function lastTutorSpeakable(): string | undefined {
    const list = bubblesRef.current;
    for (let i = list.length - 1; i >= 0; i--) {
      const b = list[i];
      if (b.role === "tutor" && b.text.trim()) return speakableText(b.text);
    }
    return undefined;
  }

  function markNeedsTap(tutorId: string) {
    setBubbles((prev) =>
      prev.map((b) => (b.id === tutorId ? { ...b, needsTap: true } : b)),
    );
  }

  function storeTtsOnBubble(tutorId: string, tts: TtsPayload) {
    setBubbles((prev) =>
      prev.map((b) =>
        b.id === tutorId
          ? {
              ...b,
              audioBase64: tts.audioBase64,
              mimeType: tts.mimeType || "audio/mpeg",
              needsTap: false,
            }
          : b,
      ),
    );
  }

  function attachSpeechHandlers(
    tutorId: string,
    tutorTextAcc: { current: string },
    spoken: { current: boolean },
    turnIndexHint?: number,
  ) {
    return {
      onDelta: (chunk: string) => {
        tutorTextAcc.current += chunk;
        setBubbles((prev) =>
          prev.map((b) =>
            b.id === tutorId ? { ...b, text: b.text + chunk } : b,
          ),
        );
      },
      onTts: (payload: TtsPayload) => {
        if (!payload.audioBase64) return;
        spoken.current = true;
        storeTtsOnBubble(tutorId, payload);
        autoListenGen.current += 1;
        playTutorVoice({
          text: tutorTextAcc.current,
          audioBase64: payload.audioBase64,
          mimeType: payload.mimeType,
          onBlocked: () => markNeedsTap(tutorId),
          onStart: () => {
            autoListenGen.current += 1;
            setCallState("speaking");
            showSpeakingEmotion(tutorTextAcc.current);
            reportE2eIfNeeded(turnIndexHint);
          },
          onEnded: () => {
            setCallState((s) => (s === "speaking" ? "idle" : s));
            setEmotion("neutral");
            scheduleAutoListen();
            maybeGoReportAfterSpeech();
          },
        });
      },
      onTiming: (payload: { kind?: string }) => {
        /* 儿童不展示；若服务端带 turnIndex 可缓存 */
        void payload;
      },
      onProp: (payload: PropStagePayload) => {
        setPropStage(toPropStageView(payload));
      },
      finish: () => {
        if (spoken.current) return;
        const full = tutorTextAcc.current.trim();
        if (!full) {
          setCallState((s) => (s === "thinking" ? "idle" : s));
          scheduleAutoListen();
          maybeGoReportAfterSpeech();
          return;
        }
        spoken.current = true;
        const bubble = bubblesRef.current.find((b) => b.id === tutorId);
        autoListenGen.current += 1;
        playTutorVoice({
          text: full,
          audioBase64: bubble?.audioBase64,
          mimeType: bubble?.mimeType,
          onBlocked: () => markNeedsTap(tutorId),
          onStart: () => {
            autoListenGen.current += 1;
            setCallState("speaking");
            showSpeakingEmotion(full);
            reportE2eIfNeeded(turnIndexHint);
          },
          onEnded: () => {
            setCallState((s) => (s === "speaking" ? "idle" : s));
            setEmotion("neutral");
            scheduleAutoListen();
            maybeGoReportAfterSpeech();
          },
        });
      },
    };
  }

  function scheduleAutoListen() {
    if (!autoListenRef.current) return;
    if (readOnlyHint) return;
    if (recorderRef.current?.recording) return;
    // 外教仍在播：绝不 stop 抢麦，延后重试
    if (isTutorVoicePlaying()) {
      const gen = ++autoListenGen.current;
      window.setTimeout(() => {
        if (gen !== autoListenGen.current) return;
        scheduleAutoListen();
      }, AUTO_LISTEN_RETRY_MS);
      return;
    }
    if (
      !shouldArmAutoListen({
        autoListen: autoListenRef.current,
        readOnly: !!readOnlyHint,
        recording: !!recorderRef.current?.recording,
        playbackLive: false,
      })
    ) {
      return;
    }
    const gen = ++autoListenGen.current;
    window.setTimeout(() => {
      if (gen !== autoListenGen.current) return;
      if (!autoListenRef.current || readOnlyHint) return;
      if (recorderRef.current?.recording) return;
      if (isTutorVoicePlaying()) {
        scheduleAutoListen();
        return;
      }
      void startAutoListen();
    }, AUTO_LISTEN_DEBOUNCE_MS);
  }

  function claimVoiceSubmit(): boolean {
    if (voiceSubmitClaimed.current) return false;
    voiceSubmitClaimed.current = true;
    return true;
  }

  function releaseVoiceSubmitClaim() {
    voiceSubmitClaimed.current = false;
  }

  async function submitVoiceCapture(capture: {
    base64: string;
    mimeType: string;
    durationMs: number;
  }) {
    if (capture.durationMs < 350) {
      setError("录音太短，请再说一句试试");
      setCallState("idle");
      releaseVoiceSubmitClaim();
      return;
    }
    const referenceText = lastTutorSpeakable();
    try {
      await streamTurn(
        {
          audioBase64: capture.base64,
          locale: "en-US",
          ...(referenceText ? { referenceText } : {}),
        },
        "（语音）",
        { audioBase64: capture.base64, mimeType: capture.mimeType || "audio/wav" },
      );
    } finally {
      releaseVoiceSubmitClaim();
    }
  }

  /** VAD 聆听：自动听调度与点「开始说话」共用，说完停顿后自动上传。 */
  async function beginVadListen(opts?: { replayPendingTutor?: boolean }) {
    if (recorderRef.current?.recording) return;
    // 外教还在出声时禁止开麦（旧逻辑会 stopTutorVoice 掐尾句）
    if (isTutorVoicePlaying()) {
      scheduleAutoListen();
      return;
    }
    releaseVoiceSubmitClaim();
    let rec: WavRecorder | null = null;
    try {
      setError("");
      stopTutorVoice();
      rec = new WavRecorder();
      recorderRef.current = rec;
      setRecording(true);
      setCallState("listening");
      if (opts?.replayPendingTutor) {
        const pending = [...bubblesRef.current]
          .reverse()
          .find((b) => b.role === "tutor" && b.needsTap);
        if (pending?.text.trim()) {
          playTutorVoice({
            text: pending.text,
            audioBase64: pending.audioBase64,
            mimeType: pending.mimeType,
            onBlocked: () => markNeedsTap(pending.id),
          });
          setBubbles((prev) =>
            prev.map((b) => (b.id === pending.id ? { ...b, needsTap: false } : b)),
          );
        }
      }
      const capture = await rec.listenUntilSilence();
      if (recorderRef.current !== rec) {
        return;
      }
      recorderRef.current = null;
      setRecording(false);
      if (!claimVoiceSubmit()) return;
      tStopRef.current = performance.now();
      setCallState("thinking");
      triggerNod();
      await submitVoiceCapture(capture);
    } catch (err) {
      const msg = err instanceof Error ? err.message : "录音失败";
      if (
        rec &&
        (recorderRef.current === rec || !recorderRef.current?.recording)
      ) {
        recorderRef.current = null;
      }
      setRecording(false);
      // 用户点了「停止并发送」后 listen 侧会收到已结束；忽略以免盖住成功态
      if (msg === RECORDER_ALREADY_ENDED) {
        return;
      }
      setError(msg);
      setCallState("idle");
    }
  }

  async function startAutoListen() {
    if (!autoListenRef.current) return;
    await beginVadListen();
  }

  async function runOpening() {
    setError("");
    setCallState("thinking");
    triggerNod();
    tStopRef.current = performance.now();
    reportedTurnRef.current = 1;
    const tutorId = `t-open-${Date.now()}`;
    setBubbles([{ id: tutorId, role: "tutor", text: "", turnIndex: 1 }]);
    let skipped = false;
    const spoken = { current: false };
    const tutorTextAcc = { current: "" };
    const speech = attachSpeechHandlers(tutorId, tutorTextAcc, spoken, 1);
    try {
      await postSse(`/api/cet/sessions/${sessionId}/stream`, { opening: true }, {
        onDelta: speech.onDelta,
        onTiming: speech.onTiming,
        onProp: speech.onProp,
        onSafety: (msg) => {
          setBubbles((prev) =>
            prev.map((b) =>
              b.id === tutorId
                ? { ...b, role: "system", text: "我们换个更有趣的话题聊聊吧～" }
                : b,
            ),
          );
          setError(msg);
          setCallState("blocked");
        },
        onError: (msg) => {
          if (msg.includes("CET_INVALID_STATE") || msg.includes("开场已发送")) {
            skipped = true;
            setBubbles((prev) => prev.filter((b) => b.id !== tutorId));
            setError("");
            setCallState("idle");
            return;
          }
          setError(msg);
          setCallState("blocked");
        },
        onTts: speech.onTts,
        onDone: speech.finish,
      });
      speech.finish();
    } catch (err) {
      if (!skipped) {
        setError(err instanceof Error ? err.message : "开场失败");
        setCallState("blocked");
      }
    } finally {
      if (!spoken.current) {
        setCallState((s) => (s === "thinking" ? "idle" : s));
      }
    }
  }

  function wrapUpStreamExtras() {
    return {
      onWrapUp: (payload: { phase?: string; step?: number }) => {
        if (payload.step === 0) {
          queueWrapUpStep1Ref.current = true;
        }
        if (payload.phase === "await_child_farewell") {
          setWrapUpHint(true);
          startFarewellTimeout();
        }
      },
      onSessionCompleted: () => {
        clearFarewellTimeout();
        markAutoCompletePending();
      },
    };
  }

  async function runWrapUpStream() {
    setError("");
    setWrapUpHint(true);
    setCallState("thinking");
    const maxIdx = bubblesRef.current.reduce(
      (m, b) => Math.max(m, b.turnIndex ?? 0),
      0,
    );
    const nextIndex = maxIdx + 1;
    const tutorId = `t-wrap1-${Date.now()}`;
    setBubbles((prev) => [
      ...prev,
      { id: tutorId, role: "tutor", text: "", turnIndex: nextIndex },
    ]);
    const spoken = { current: false };
    const tutorTextAcc = { current: "" };
    const speech = attachSpeechHandlers(tutorId, tutorTextAcc, spoken, nextIndex);
    try {
      await postSse(`/api/cet/sessions/${sessionId}/stream`, { wrapUp: true }, {
        onDelta: speech.onDelta,
        onTiming: speech.onTiming,
        onProp: speech.onProp,
        onTts: speech.onTts,
        onError: (msg) => {
          setError(msg);
          setCallState("blocked");
        },
        onDone: speech.finish,
        ...wrapUpStreamExtras(),
      });
      speech.finish();
    } catch (err) {
      setError(err instanceof Error ? err.message : "告别失败");
      setCallState("blocked");
    }
  }

  async function runWrapUpTimeout() {
    if (readOnlyHint) return;
    setError("");
    setCallState("thinking");
    const maxIdx = bubblesRef.current.reduce(
      (m, b) => Math.max(m, b.turnIndex ?? 0),
      0,
    );
    const nextIndex = maxIdx + 1;
    const tutorId = `t-wrap2-${Date.now()}`;
    setBubbles((prev) => [
      ...prev,
      { id: tutorId, role: "tutor", text: "", turnIndex: nextIndex },
    ]);
    const spoken = { current: false };
    const tutorTextAcc = { current: "" };
    const speech = attachSpeechHandlers(tutorId, tutorTextAcc, spoken, nextIndex);
    try {
      await postSse(
        `/api/cet/sessions/${sessionId}/stream`,
        { wrapUpTimeout: true },
        {
          onDelta: speech.onDelta,
          onTiming: speech.onTiming,
          onProp: speech.onProp,
          onTts: speech.onTts,
          onError: (msg) => {
            setError(msg);
            setCallState("blocked");
          },
          onDone: speech.finish,
          ...wrapUpStreamExtras(),
        },
      );
      speech.finish();
    } catch (err) {
      setError(err instanceof Error ? err.message : "自动结课失败");
      setCallState("blocked");
    }
  }

  async function streamTurn(
    body: { text?: string; audioBase64?: string; locale?: string; referenceText?: string },
    childLabel: string,
    childAudio?: { audioBase64: string; mimeType: string },
  ) {
    if (wrapUpHint) clearFarewellTimeout();
    setError("");
    setCallState("thinking");
    const maxIdx = bubblesRef.current.reduce(
      (m, b) => Math.max(m, b.turnIndex ?? 0),
      0,
    );
    const nextIndex = maxIdx + 1;
    reportedTurnRef.current = nextIndex;
    const childId = `c-${Date.now()}`;
    const tutorId = `t-${Date.now()}`;
    if (childAudio?.audioBase64) {
      childAudioRef.current.set(childId, {
        audioBase64: childAudio.audioBase64,
        mimeType: childAudio.mimeType || "audio/wav",
      });
    }
    setBubbles((prev) => [
      ...prev,
      {
        id: childId,
        role: "child",
        text: childLabel,
        turnIndex: nextIndex,
        ...(childAudio?.audioBase64 ? { hasLocalAudio: true } : {}),
      },
      { id: tutorId, role: "tutor", text: "", turnIndex: nextIndex },
    ]);
    const spoken = { current: false };
    const tutorTextAcc = { current: "" };
    const speech = attachSpeechHandlers(tutorId, tutorTextAcc, spoken, nextIndex);
    try {
      await postSse(`/api/cet/sessions/${sessionId}/stream`, body, {
        onDelta: speech.onDelta,
        onTiming: speech.onTiming,
        onProp: speech.onProp,
        onAsr: (payload) => {
          const t = (payload.text || "").trim();
          if (!t) return;
          setBubbles((prev) =>
            prev.map((b) => (b.id === childId ? { ...b, text: t } : b)),
          );
        },
        onSafety: (msg) => {
          setBubbles((prev) =>
            prev.map((b) =>
              b.id === tutorId
                ? { ...b, role: "system", text: "我们换个更有趣的话题聊聊吧～" }
                : b,
            ),
          );
          setError(msg);
          setCallState("blocked");
        },
        onError: (msg) => {
          setError(msg);
          setCallState("blocked");
        },
        onTts: speech.onTts,
        onDone: speech.finish,
        ...wrapUpStreamExtras(),
      });
      speech.finish();
      if (queueWrapUpStep1Ref.current) {
        queueWrapUpStep1Ref.current = false;
        void runWrapUpStream();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "发送失败");
      setCallState("blocked");
    } finally {
      if (!spoken.current) {
        setCallState((s) => (s === "thinking" ? "idle" : s));
      }
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    if (!text.trim() || busy || recording) return;
    const childText = text.trim();
    setText("");
    tStopRef.current = performance.now();
    await streamTurn({ text: childText }, childText);
  }

  async function toggleRecord() {
    if (busy && !recording) return;
    if (recording) {
      const rec = recorderRef.current;
      if (!rec?.recording) {
        setRecording(false);
        recorderRef.current = null;
        return;
      }
      try {
        autoListenGen.current += 1;
        const capture = await rec.stop();
        recorderRef.current = null;
        setRecording(false);
        if (!claimVoiceSubmit()) return;
        tStopRef.current = performance.now();
        setCallState("thinking");
        triggerNod();
        await submitVoiceCapture(capture);
      } catch (err) {
        setRecording(false);
        recorderRef.current = null;
        const msg = err instanceof Error ? err.message : "录音失败";
        if (msg === RECORDER_ALREADY_ENDED) {
          setCallState("idle");
          return;
        }
        setError(msg);
        setCallState("idle");
      }
      return;
    }
    autoListenGen.current += 1;
    if (autoListenRef.current) {
      void beginVadListen({ replayPendingTutor: true });
      return;
    }
    releaseVoiceSubmitClaim();
    try {
      setError("");
      stopTutorVoice();
      const rec = new WavRecorder();
      await rec.start();
      recorderRef.current = rec;
      setRecording(true);
      setCallState("listening");
      const pending = [...bubblesRef.current]
        .reverse()
        .find((b) => b.role === "tutor" && b.needsTap);
      if (pending?.text.trim()) {
        playTutorVoice({
          text: pending.text,
          audioBase64: pending.audioBase64,
          mimeType: pending.mimeType,
          onBlocked: () => markNeedsTap(pending.id),
        });
        setBubbles((prev) =>
          prev.map((b) => (b.id === pending.id ? { ...b, needsTap: false } : b)),
        );
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "无法打开麦克风");
    }
  }

  function toggleAutoListenPrefer() {
    setAutoListen((prev) => {
      const next = !prev;
      localStorage.setItem(AUTO_LISTEN_KEY, next ? "1" : "0");
      autoListenRef.current = next;
      if (!next) {
        autoListenGen.current += 1;
      }
      return next;
    });
  }

  function pauseLesson() {
    clearFarewellTimeout();
    autoListenGen.current += 1;
    stopTutorVoice();
    const rec = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);
    rec?.cancel();
    router.push("/home");
  }

  async function complete() {
    if (completing || readOnlyHint) return;
    clearFarewellTimeout();
    setCompleting(true);
    setError("");
    // 自动听/录音中也允许手动结课：先掐断开麦与待调度，避免继续上传
    autoListenGen.current += 1;
    stopTutorVoice();
    const rec = recorderRef.current;
    recorderRef.current = null;
    setRecording(false);
    rec?.cancel();
    setCallState("thinking");
    try {
      await apiJson(`/api/cet/sessions/${sessionId}/complete`, {
        method: "POST",
        body: "{}",
      });
      router.push(`/cet/report/${sessionId}`);
    } catch (err) {
      setCompleting(false);
      setError(err instanceof Error ? err.message : "结课失败");
      setCallState("idle");
    }
  }

  async function replayBubble(b: Bubble) {
    if (b.role === "child") {
      const local =
        childAudioRef.current.get(b.id) ||
        (b.audioBase64
          ? { audioBase64: b.audioBase64, mimeType: b.mimeType || "audio/wav" }
          : null);
      if (!local?.audioBase64) {
        setError("这句原音仅本场可听，刷新后无法回放");
        return;
      }
      playTutorVoice({
        audioBase64: local.audioBase64,
        mimeType: local.mimeType || "audio/wav",
        allowBrowserFallback: false,
      });
      return;
    }
    if (b.role === "tutor" && b.turnIndex != null) {
      if (b.audioBase64) {
        setBubbles((prev) =>
          prev.map((x) => (x.id === b.id ? { ...x, needsTap: false } : x)),
        );
        playTutorVoice({
          text: b.text,
          audioBase64: b.audioBase64,
          mimeType: b.mimeType,
          onBlocked: () => markNeedsTap(b.id),
          onStart: () => setCallState("speaking"),
          onEnded: () => setCallState("idle"),
        });
        return;
      }
      try {
        const tts = await apiJson<TtsPayload>(
          `/api/cet/sessions/${sessionId}/turns/${b.turnIndex}/tts`,
          { method: "POST", body: "{}" },
        );
        if (tts?.audioBase64) {
          setBubbles((prev) =>
            prev.map((x) =>
              x.id === b.id
                ? {
                    ...x,
                    audioBase64: tts.audioBase64,
                    mimeType: tts.mimeType || "audio/mpeg",
                    needsTap: false,
                  }
                : x,
            ),
          );
          playTutorVoice({
            text: b.text,
            audioBase64: tts.audioBase64,
            mimeType: tts.mimeType,
            onBlocked: () => markNeedsTap(b.id),
            onStart: () => setCallState("speaking"),
            onEnded: () => setCallState("idle"),
          });
          return;
        }
        setError("外教语音合成失败，请稍后重试");
      } catch {
        setError("外教语音合成失败，请稍后重试");
      }
      setBubbles((prev) =>
        prev.map((x) => (x.id === b.id ? { ...x, needsTap: false } : x)),
      );
    }
  }

  function canReplay(b: Bubble | undefined): boolean {
    if (!b) return false;
    if (b.role === "tutor" && b.text.trim()) return true;
    if (b.role === "child") {
      return (
        !!b.hasLocalAudio ||
        !!b.audioBase64 ||
        childAudioRef.current.has(b.id)
      );
    }
    return false;
  }

  return (
    <main className="shell call-shell call-shell--immersive" data-theme={themeId}>
      <div className="theme-motif" aria-hidden="true" />
      <header className="call-chrome">
        <div className="topbar">
          <Link className="mini-brand" href="/home">
            Kidora · CET
          </Link>
          <label className="auto-listen-toggle">
            <input
              type="checkbox"
              checked={autoListen}
              onChange={toggleAutoListenPrefer}
            />
            自动听
          </label>
          <button
            className="btn ghost"
            type="button"
            onClick={pauseLesson}
            disabled={!!readOnlyHint}
          >
            暂停
          </button>
          <button
            className="btn ghost"
            type="button"
            onClick={() => void complete()}
            disabled={completing || !!readOnlyHint}
          >
            结束并总结
          </button>
        </div>
        {childGoals.length > 0 || planSummary ? (
          <details className="session-plan-details">
            <summary>本课目标{childGoals.length ? ` · ${goalStep}/3` : ""}</summary>
            {planSummary ? <p className="session-plan-hint">{planSummary}</p> : null}
            {childGoals.length > 0 ? (
              <ol className="lesson-goals">
                {childGoals.slice(0, 3).map((g, i) => (
                  <li key={i} className={goalStep === i + 1 ? "active" : undefined}>
                    <span className="goal-idx">{i + 1}/3</span>
                    <span>{g}</span>
                  </li>
                ))}
              </ol>
            ) : null}
          </details>
        ) : null}
      </header>

      <div className="call-stage">
        <PersonaStage
          personaId={personaId}
          callState={callState}
          statusLabel={statusLabel(callState)}
          themeId={themeId}
          nodding={nodding}
          sticker={sticker}
          layout={effectivePropStage.layout}
          propAssets={effectivePropStage.assets}
          activeLemma={effectivePropStage.activeLemma}
        />

        <div className="call-overlays">
          {!historyOpen ? (
            <div className="caption-strip call-captions">
              <div
                className={`caption-line tutor${captionExpanded ? " is-expanded" : ""}`}
              >
                <span className="caption-role">外教</span>
                <p className="caption-text">{tutorCaptionText}</p>
                <div className="caption-actions">
                  {tutorCaptionLong ? (
                    <button
                      type="button"
                      className="caption-expand"
                      onClick={() => setCaptionExpanded((v) => !v)}
                    >
                      {captionExpanded ? "收起" : "全文"}
                    </button>
                  ) : null}
                  {canReplay(captionTutor) ? (
                    <button
                      type="button"
                      className={`bubble-speak${captionTutor?.needsTap ? " needs-tap" : ""}`}
                      aria-label="播放外教语音"
                      onClick={() => void replayBubble(captionTutor!)}
                    >
                      {captionTutor?.needsTap ? "点这里听" : "播放"}
                    </button>
                  ) : null}
                </div>
              </div>
              {captionChild ? (
                <div className="caption-line child">
                  <span className="caption-role">你</span>
                  <p className="caption-text">{captionChild.text}</p>
                  {canReplay(captionChild) ? (
                    <button
                      type="button"
                      className="bubble-speak"
                      aria-label="播放我的录音"
                      onClick={() => void replayBubble(captionChild)}
                    >
                      播放
                    </button>
                  ) : null}
                </div>
              ) : null}
            </div>
          ) : null}

          {!historyOpen ? (
            <button
              type="button"
              className="history-toggle"
              onClick={() => setHistoryOpen(true)}
              aria-expanded={false}
              aria-controls="cet-call-history"
            >
              {`查看对话历史 · ${rounds} 轮 ▾`}
            </button>
          ) : (
            <div
              id="cet-call-history"
              className="call-history-sheet"
              role="dialog"
              aria-label="对话历史"
            >
              <div className="history-sheet-head">
                <span className="history-sheet-title">对话历史 · {rounds} 轮</span>
                <button
                  type="button"
                  className="history-sheet-close"
                  onClick={() => setHistoryOpen(false)}
                >
                  收起
                </button>
              </div>
              <div className="history-compact">
                {bubbles.length === 0 ? (
                  <p className="history-empty">还没有对话，先说一句试试～</p>
                ) : (
                  bubbles.map((b) => (
                    <div key={b.id} className={`history-row ${b.role}`}>
                      <span className="history-role">
                        {b.role === "tutor" ? "外教" : b.role === "child" ? "你" : "系统"}
                      </span>
                      <span className="history-text">{b.text || "…"}</span>
                      {canReplay(b) ? (
                        <button
                          type="button"
                          className={`bubble-speak${b.needsTap ? " needs-tap" : ""}`}
                          aria-label={
                            b.role === "tutor" ? "播放外教语音" : "播放我的录音"
                          }
                          onClick={() => void replayBubble(b)}
                        >
                          {b.needsTap ? "点这里听" : "播放"}
                        </button>
                      ) : b.role === "child" ? (
                        <span className="history-no-audio" title="原音仅本场内存可回放">
                          —
                        </span>
                      ) : null}
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          <div className="call-dock call-dock-capsule">
            {readOnlyHint ? <p className="lead">{readOnlyHint}</p> : null}
            {wrapUpHint && !readOnlyHint ? (
              <p className="lead">跟老师说再见吧～</p>
            ) : null}
            {error ? <p className="error">{error}</p> : null}
            {!readOnlyHint && ready ? (
              <>
                <div className="composer voice-first">
                  <button
                    className={`btn speak-main ${recording ? "recording" : ""}`}
                    type="button"
                    onClick={toggleRecord}
                    disabled={busy && !recording}
                    aria-pressed={recording}
                  >
                    {recording ? "停止并发送" : autoListen ? "开始说话" : "录音"}
                  </button>
                  <button
                    className="btn ghost"
                    type="button"
                    onClick={() => setShowTextInput((v) => !v)}
                    disabled={busy || recording}
                  >
                    {showTextInput ? "收起打字" : "打字"}
                  </button>
                </div>
                {showTextInput ? (
                  <form className="composer text-secondary" onSubmit={send}>
                    <input
                      value={text}
                      onChange={(e) => setText(e.target.value)}
                      placeholder="用英语或中文说说看…"
                      disabled={busy || recording}
                    />
                    <button
                      className="btn"
                      type="submit"
                      disabled={busy || recording || !text.trim()}
                    >
                      {callState === "thinking" ? "思考中" : "发送"}
                    </button>
                  </form>
                ) : null}
              </>
            ) : null}
            {readOnlyHint ? (
              <div className="history-actions">
                <Link className="btn ghost" href={`/cet/history/${sessionId}`}>
                  回看对话
                </Link>
              </div>
            ) : null}
          </div>
        </div>
      </div>
    </main>
  );
}
