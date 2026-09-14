/**
 * CET tutor personas and static asset helpers.
 * @author liudy
 */

export const PERSONAS = [
  {
    id: "emma",
    name: "Emma",
    tagline: "温柔鼓励，适合刚开口",
    color: "var(--persona-emma)",
    initial: "E",
  },
  {
    id: "mike",
    name: "Mike",
    tagline: "探险任务，边玩边说",
    color: "var(--persona-mike)",
    initial: "M",
  },
  {
    id: "lily",
    name: "Lily",
    tagline: "故事世界，想象力满满",
    color: "var(--persona-lily)",
    initial: "L",
  },
  {
    id: "tom",
    name: "Tom",
    tagline: "小挑战，帮你更自信",
    color: "var(--persona-tom)",
    initial: "T",
  },
  {
    id: "coco",
    name: "Coco",
    tagline: "萌宠伙伴，低龄友好",
    color: "var(--persona-coco)",
    initial: "C",
  },
  {
    id: "alex",
    name: "Alex",
    tagline: "关卡游戏，完成小目标",
    color: "var(--persona-alex)",
    initial: "A",
  },
] as const;

export type PersonaId = (typeof PERSONAS)[number]["id"];

export type PersonaStillState =
  | "idle"
  | "listening"
  | "thinking"
  | "smile"
  | "clap"
  | "speaking";

export function personaById(id: string | null | undefined) {
  const found = PERSONAS.find((p) => p.id === id);
  return found ?? PERSONAS[0];
}

export function personaStillUrl(
  personaId: string,
  state: PersonaStillState,
): string {
  return `/personas/${personaId}/${state}.webp`;
}

export function personaSpeakingVideoUrl(personaId: string): string {
  // bump when speaking loops are regenerated so browsers skip stale cache
  return `/personas/${personaId}/speaking.webm?v=3`;
}
