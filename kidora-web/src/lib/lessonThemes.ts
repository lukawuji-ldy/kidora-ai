/**
 * Map CET lesson topic text to a visual theme pack (frontend-only).
 * @author liudy
 */

export const THEME_IDS = ["pets", "colors", "food", "default"] as const;

export type ThemeId = (typeof THEME_IDS)[number];

const PETS_KEYS = [
  "宠物",
  "小动物",
  "动物",
  "小狗",
  "小猫",
  "狗",
  "猫",
  "鱼",
  "鸟",
  "仓鼠",
  "pet",
  "pets",
  "animal",
  "animals",
  "dog",
  "cat",
  "fish",
  "bird",
  "hamster",
] as const;

const COLORS_KEYS = [
  "颜色",
  "色彩",
  "红",
  "蓝",
  "绿",
  "黄",
  "color",
  "colors",
  "colours",
  "red",
  "blue",
  "green",
  "yellow",
] as const;

const FOOD_KEYS = [
  "食物",
  "水果",
  "吃",
  "早餐",
  "food",
  "fruit",
  "eat",
  "breakfast",
  "apple",
] as const;

function includesAny(haystack: string, keys: readonly string[]): boolean {
  return keys.some((k) => haystack.includes(k.toLowerCase()));
}

/**
 * Priority: pets > colors > food > default.
 * Also accepts planSummary snippets when topic is thin.
 */
export function themeFromTopic(
  topic: string | null | undefined,
  fallbackText?: string | null,
): ThemeId {
  const primary = (topic || "").trim().toLowerCase();
  const secondary = (fallbackText || "").trim().toLowerCase();
  const t = primary || secondary;
  if (!t) return "default";
  const probe = primary ? `${primary}\n${secondary}` : secondary;
  if (includesAny(probe, PETS_KEYS)) return "pets";
  if (includesAny(probe, COLORS_KEYS)) return "colors";
  if (includesAny(probe, FOOD_KEYS)) return "food";
  return "default";
}

export function themeLabel(themeId: ThemeId): string {
  switch (themeId) {
    case "pets":
      return "宠物主题";
    case "colors":
      return "颜色主题";
    case "food":
      return "食物主题";
    default:
      return "趣味课堂";
  }
}

export function themeMotifUrl(themeId: ThemeId): string {
  return `/themes/${themeId}/motif.svg`;
}
