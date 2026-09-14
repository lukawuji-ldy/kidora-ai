# CET 提示词 V22 · 反重复 / Look 指认 / 结课错例 / 纠音

- **日期：** 2026-09-13
- **仓库：** `kidora-ai`
- **状态：** Implemented（Flyway V22）
- **关联：** [cet-tutor-design.md](../../cet-tutor-design.md)、[cet-assessment-design.md](../../cet-assessment-design.md)、[2026-09-13-cet-tutor-question-variety-design.md](./2026-09-13-cet-tutor-question-variety-design.md)、[2026-09-13-cet-prop-stage-library-design.md](./2026-09-13-cet-prop-stage-library-design.md)

## 1. 背景

回看课 `cls_5e8492dc0ac1450dba4048b07469ac19`（Animals and pets / A1）暴露四点：

1. 同骨架连环问（`Do you have a pet?` 多轮复现）
2. 道具几乎不出：前端教学态依赖字幕含 `Look` / `指认` / `What is this` 等，本课几乎只有开场用了 Look
3. 结课 `childSummary` 空泛，尽管 `problems` 已有错例
4. ASR 口误未跟读纠音（本期不接发音分回灌）

## 2. 决策

| 项 | 选择 |
|---|---|
| 范围 | 仅 Prompt Flyway 增版 + docs |
| 方案 | Tutor + Opening + Planner + Replan + Eval 联改 |
| 道具 | 靠话术触发现有 `isTeachingCaption`；不放宽前端、不注入 `availableProps` |
| 纠音 | 从孩子文本识别口误 → `Say:` 跟读 |
| 结课 | `childSummary` 强制「原话 → 正确说法」 |

## 3. 交付

- 迁移：[`V22__cet_prompt_props_repeat_eval.sql`](../../../kidora-agent-server/src/main/resources/db/migration/V22__cet_prompt_props_repeat_eval.sql)
- `change_note = 'V22 props repeat eval pron'`（幂等）
- 模板：`cet.tutor.system` / `cet.tutor.user` / `cet.tutor.opening.system` / `cet.planner.system` / `cet.replan.system` / `cet.eval.system` / `cet.eval.user`

## 4. 非目标

- 不改 `TutorLoop` 变量注入
- 不把 pronunciation SSE 写回 Tutor
- 不改 history 页字段
