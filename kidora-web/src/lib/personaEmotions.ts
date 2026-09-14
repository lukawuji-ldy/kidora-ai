/**
 * 外教文案 → speaking 阶段情绪贴纸（前端启发式，无后端字段）。
 *
 * @author liudy
 */

export type SpeakingEmotion = "clap" | "smile" | "neutral";

const CLAP_KEYWORDS = [
  "太棒",
  "真棒",
  "了不起",
  "great job",
  "well done",
  "amazing",
  "高五",
  "鼓掌",
];

const SMILE_KEYWORDS = [
  "不错",
  "很好",
  "加油",
  "试得很好",
  "good try",
  "great try",
  "nice",
  "👍",
];

function containsAny(haystack: string, needles: string[]): boolean {
  const lower = haystack.toLowerCase();
  return needles.some((n) => lower.includes(n.toLowerCase()));
}

/**
 * 对当前轮外教全文做关键词命中；clap 优先于 smile。
 */
export function inferSpeakingEmotion(tutorText: string): SpeakingEmotion {
  if (!tutorText || !tutorText.trim()) return "neutral";
  if (containsAny(tutorText, CLAP_KEYWORDS)) return "clap";
  if (containsAny(tutorText, SMILE_KEYWORDS)) return "smile";
  return "neutral";
}
