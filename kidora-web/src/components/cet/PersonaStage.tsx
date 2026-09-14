"use client";

/**
 * Large tutor presence stage: half-body photoreal stills + speaking loop.
 * Teaching mode: left/right propFocus split when propAssets match caption.
 * @author liudy
 */

import { useEffect, useMemo, useRef, useState } from "react";
import { fetchAuthedBlobUrl } from "@/lib/api";
import { themeLabel, ThemeId } from "@/lib/lessonThemes";
import type { PropAsset, PropStageLayout } from "@/lib/lessonProps";
import {
  personaById,
  personaSpeakingVideoUrl,
  personaStillUrl,
  PersonaStillState,
} from "@/lib/personas";

export type CallPresenceState =
  | "idle"
  | "listening"
  | "thinking"
  | "speaking"
  | "blocked";

type Props = {
  personaId: string;
  callState: CallPresenceState;
  statusLabel: string;
  themeId?: ThemeId;
  nodding?: boolean;
  sticker?: "smile" | "clap" | null;
  /** @deprecated corner prop; prefer layout + propAssets */
  propUrl?: string | null;
  /** @deprecated */
  propUrls?: string[] | null;
  layout?: PropStageLayout;
  propAssets?: PropAsset[] | null;
  activeLemma?: string | null;
};

function stillForState(
  callState: CallPresenceState,
  sticker: "smile" | "clap" | null,
): PersonaStillState {
  if (callState !== "speaking" && sticker === "smile") return "smile";
  if (callState !== "speaking" && sticker === "clap") return "clap";
  switch (callState) {
    case "listening":
      return "listening";
    case "thinking":
      return "thinking";
    case "speaking":
      return "speaking";
    case "blocked":
    case "idle":
    default:
      return "idle";
  }
}

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    const apply = () => setReduced(mq.matches);
    apply();
    mq.addEventListener("change", apply);
    return () => mq.removeEventListener("change", apply);
  }, []);
  return reduced;
}

function needsAuthFetch(url: string): boolean {
  return url.startsWith("/api/");
}

/** 取图失败时的最终占位；道具素材的唯一来源是服务端道具库。 */
const PROP_PLACEHOLDER = "/props/default/star.svg";

function AuthedPropImg({
  src,
  alt,
  className,
  onDead,
}: {
  src: string;
  alt: string;
  className?: string;
  onDead: () => void;
}) {
  const [display, setDisplay] = useState<string | null>(
    needsAuthFetch(src) ? null : src,
  );
  const onDeadRef = useRef(onDead);
  onDeadRef.current = onDead;

  useEffect(() => {
    let cancelled = false;
    if (!needsAuthFetch(src)) {
      setDisplay(src);
      return;
    }
    setDisplay(null);
    fetchAuthedBlobUrl(src)
      .then((url) => {
        if (!cancelled) setDisplay(url);
      })
      .catch(() => {
        if (!cancelled) setDisplay(PROP_PLACEHOLDER);
      });
    return () => {
      cancelled = true;
    };
  }, [src]);

  if (!display) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      className={className}
      src={display}
      alt={alt}
      draggable={false}
      onError={() => {
        if (display !== PROP_PLACEHOLDER) {
          setDisplay(PROP_PLACEHOLDER);
          return;
        }
        onDeadRef.current();
      }}
    />
  );
}

export function PersonaStage({
  personaId,
  callState,
  statusLabel,
  themeId = "default",
  nodding = false,
  sticker = null,
  propUrl = null,
  propUrls = null,
  layout = "personaFocus",
  propAssets = null,
  activeLemma = null,
}: Props) {
  const persona = personaById(personaId);
  const reducedMotion = usePrefersReducedMotion();
  const stillState = stillForState(callState, sticker);
  const stillSrc = personaStillUrl(persona.id, stillState);
  const idleSrc = personaStillUrl(persona.id, "idle");
  const videoSrc = personaSpeakingVideoUrl(persona.id);

  const [deadLemmas, setDeadLemmas] = useState<Set<string>>(() => new Set());

  const liveAssets = useMemo(() => {
    const fromSession = (propAssets || []).filter(
      (a) => a?.url && a?.lemma && !deadLemmas.has(a.lemma.toLowerCase()),
    );
    if (fromSession.length > 0) return fromSession;
    // legacy fallback paths (static public)
    if (propUrls && propUrls.length > 0) {
      return propUrls.slice(0, 3).map((url, i) => ({
        lemma: `legacy-${i}`,
        theme: themeId,
        url,
      }));
    }
    if (propUrl) {
      return [{ lemma: "legacy", theme: themeId, url: propUrl }];
    }
    return [] as PropAsset[];
  }, [propAssets, propUrls, propUrl, deadLemmas, themeId]);

  const effectiveLayout: PropStageLayout =
    layout === "propFocus" && liveAssets.length > 0 ? "propFocus" : "personaFocus";

  const [imgOk, setImgOk] = useState(true);
  const [imgSrc, setImgSrc] = useState(stillSrc);
  const [videoFailed, setVideoFailed] = useState(false);

  useEffect(() => {
    setImgOk(true);
    setImgSrc(stillSrc);
  }, [stillSrc]);

  useEffect(() => {
    setVideoFailed(false);
  }, [videoSrc, callState]);

  useEffect(() => {
    setDeadLemmas(new Set());
  }, [propAssets, propUrl, propUrls]);

  const useVideo =
    callState === "speaking" && !reducedMotion && !videoFailed;

  const layoutClass =
    effectiveLayout === "propFocus" ? "layout-prop-focus" : "layout-persona-focus";
  const ringClass = useMemo(
    () =>
      `persona-stage halfbody state-${callState} ${layoutClass}${nodding ? " nodding" : ""}`,
    [callState, nodding, layoutClass],
  );

  const active =
    (activeLemma &&
      liveAssets.find((a) => a.lemma.toLowerCase() === activeLemma.toLowerCase())) ||
    liveAssets[0] ||
    null;
  const thumbs = liveAssets.filter(
    (a) => !active || a.lemma.toLowerCase() !== active.lemma.toLowerCase(),
  );

  const markDead = (lemma: string) => {
    setDeadLemmas((prev) => {
      const next = new Set(prev);
      next.add(lemma.toLowerCase());
      return next;
    });
  };

  const portrait = (
    <div className="persona-avatar" style={{ background: persona.color }}>
      <p className="theme-chip persona-theme-chip" data-theme-chip={themeId}>
        {themeLabel(themeId)}
      </p>
      {useVideo ? (
        <video
          key={`${videoSrc}-${callState}`}
          className="persona-media persona-speaking-video"
          src={videoSrc}
          muted
          playsInline
          loop
          autoPlay
          preload="auto"
          poster={stillSrc}
          aria-hidden="true"
          onLoadedData={(e) => {
            const v = e.currentTarget;
            v.playbackRate = 1;
            void v.play().catch(() => setVideoFailed(true));
          }}
          onError={() => setVideoFailed(true)}
        />
      ) : null}
      {!useVideo && imgOk ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          key={imgSrc}
          className="persona-media"
          src={imgSrc}
          alt=""
          draggable={false}
          onError={() => {
            if (imgSrc !== idleSrc) {
              setImgSrc(idleSrc);
              return;
            }
            setImgOk(false);
          }}
        />
      ) : null}
      {!useVideo && !imgOk ? (
        <span className="persona-initial" aria-hidden="true">
          {persona.initial}
        </span>
      ) : null}
      <div className="persona-meta">
        <p className="persona-name">{persona.name}</p>
        <p className="persona-status">{statusLabel}</p>
      </div>
      {effectiveLayout === "personaFocus" && liveAssets.length > 0 ? (
        <div
          className={`persona-prop${liveAssets.length > 1 ? " persona-prop-strip" : ""}`}
          aria-hidden="true"
        >
          {liveAssets.map((a) => (
            <AuthedPropImg
              key={a.lemma}
              src={a.url}
              alt=""
              onDead={() => markDead(a.lemma)}
            />
          ))}
        </div>
      ) : null}
    </div>
  );

  return (
    <section className={ringClass} aria-live="polite">
      <div className="persona-stage-glow" aria-hidden="true" />
      <div className="persona-ring" aria-hidden="true" />
      {effectiveLayout === "propFocus" ? (
        <div className="persona-split">
          <div className="persona-split-portrait">{portrait}</div>
          <div className="persona-split-props" role="img" aria-label={active?.lemma || "prop"}>
            {active ? (
              <div className="prop-focus-card">
                <AuthedPropImg
                  src={active.url}
                  alt={active.lemma}
                  className="prop-focus-main"
                  onDead={() => markDead(active.lemma)}
                />
                <p className="prop-focus-lemma">{active.lemma}</p>
              </div>
            ) : null}
            {thumbs.length > 0 ? (
              <div className="prop-focus-thumbs" aria-hidden="true">
                {thumbs.map((a) => (
                  <AuthedPropImg
                    key={a.lemma}
                    src={a.url}
                    alt=""
                    className="prop-focus-thumb"
                    onDead={() => markDead(a.lemma)}
                  />
                ))}
              </div>
            ) : null}
          </div>
        </div>
      ) : (
        portrait
      )}
      {sticker ? (
        <div className={`persona-sticker sticker-${sticker}`} aria-hidden="true" />
      ) : null}
    </section>
  );
}
