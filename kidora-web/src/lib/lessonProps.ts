/**
 * Lesson prop stage types.
 *
 * 「显示哪张道具图」由后端 PropStageDirector 判定，通过 SSE `turn.prop` 事件下发；
 * 前端不再从字幕文本推断，因此这里只保留类型与展示无关的小工具。
 *
 * @author liudy
 */

export type PropAsset = {
  lemma: string;
  theme: string;
  url: string;
};

export type PropStageLayout = "personaFocus" | "propFocus";

export type PropStageView = {
  layout: PropStageLayout;
  /** Ordered assets to show (active first when propFocus). */
  assets: PropAsset[];
  activeLemma: string | null;
};

export const PERSONA_FOCUS_STAGE: PropStageView = {
  layout: "personaFocus",
  assets: [],
  activeLemma: null,
};

/** 把 SSE payload 归一为舞台视图；字段缺失或非法时收起分屏。 */
export function toPropStageView(payload: unknown): PropStageView {
  if (!payload || typeof payload !== "object") return PERSONA_FOCUS_STAGE;
  const raw = payload as {
    layout?: string;
    activeLemma?: string | null;
    assets?: Array<Partial<PropAsset>>;
  };
  const assets = (raw.assets || [])
    .filter((a): a is PropAsset => Boolean(a?.lemma && a?.url))
    .map((a) => ({ lemma: a.lemma, theme: a.theme || "default", url: a.url }));
  if (raw.layout !== "propFocus" || assets.length === 0) return PERSONA_FOCUS_STAGE;
  return {
    layout: "propFocus",
    assets,
    activeLemma: raw.activeLemma || assets[0].lemma,
  };
}

/** Goal step 1..3 from rough turn count (frontend-only). */
export function goalStepFromTurnCount(turnCount: number): 1 | 2 | 3 {
  if (turnCount <= 3) return 1;
  if (turnCount <= 8) return 2;
  return 3;
}
