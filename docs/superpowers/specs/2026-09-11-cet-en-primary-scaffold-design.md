# CET 练习轮英文为主脚手架设计

- **日期：** 2026-09-11
- **仓库：** `kidora-ai`
- **状态：** Implemented
- **关联：** `cet.tutor.system`（V15 / V17）；提问多样化见 [2026-09-13-cet-tutor-question-variety-design.md](./2026-09-13-cet-tutor-question-variety-design.md)；开场仍见 [2026-09-10-cet-opening-en-greeting-design.md](./2026-09-10-cet-opening-en-greeting-design.md)；TTS 剥括号见既有 `speakableForTts`
- **取代：** [2026-09-10-cet-tutor-zh-scaffold-tts-design.md](./2026-09-10-cet-tutor-zh-scaffold-tts-design.md) 中「A0/A1 鼓励/追问默认中文」口径（TTS 剥括号规则保留）

## 1. 背景与目标

V12 中文脚手架让 A0/A1 练习轮鼓励、引导、下一问默认中文，现场感觉「中文辅助太多」，削弱英语沉浸。

**目标：**

1. 练习轮默认 **英文主表达**；**整轮最多 1 处** `(中文注释)`，优先挂在下一问后。
2. **中文正文**仅保留：显式纠错讲解、孩子中文求助时的脚手架；禁止中文鼓励主句（如「太棒了」）。
3. 开场 A0/A1 三段式（英问候 + 中文点题 + 英问）不变。
4. TTS 仍读中英正文、不读含中文的括号注释。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 练习轮默认语言 | 英文鼓励 / 隐式纠错 / 下一问 |
| 括号注释 | **每轮最多 1 处**；优先只注释下一问；禁止逐句括号翻译 |
| 中文正文触发 | 显式纠错讲解；孩子中文求助 |
| A0/A1 下一问 | **英文** + 可选唯一括号中文 |
| 开场 | 不改 V13 |
| 实现 | Flyway Prompt（V15 增版 + **V17 再收紧**）+ 文档 |

### 1.2 非目标

- 不做 `zhGuide` / `enExamples` 结构化字段
- 不改 Safety / Planner / Eval 主逻辑（Planner `childGoals` 另见配套规格）
- 不改 `speakableForTts` 行为

## 2. 话术规则（练习轮）

1. 每轮短：先鼓励 → 按需纠错 → **一个**下一问。
2. 默认隐式纠错：正确英语自然复述再追问。
3. 平常轮：**鼓励必须英文**；示范答句不加中文括号。
4. 显式纠错：短英文鼓励 → 1～2 句**中文**讲解 → 英文关键点 → 英问（可附那唯一括号中文）。
5. 孩子中文求助：短中文讲解 + 英文示范 + 英问。
6. 目标形态（V17 示意；**题型轮换与反重复见** [2026-09-13-cet-tutor-question-variety-design.md](./2026-09-13-cet-tutor-question-variety-design.md)）：勿每轮锁死 yes/no。

## 3. 与开场关系

| 场景 | 语言 |
|---|---|
| 开场 A0/A1 | 英问候介绍 → **中文点题** → 英问（括号中文） |
| 练习轮平常 | **全英文主句** + 至多 1 处括号中文 |
| 练习轮纠错/中文求助 | 短中文讲解 + 英文示范 |

## 4. 实现

- `V15__cet_tutor_en_primary_scaffold.sql`：首次改为英文为主（增版）
- `V16__prompt_en_primary_version_repair.sql`：旧 V15 原地覆盖修复
- `V17__cet_tutor_en_primary_tighten.sql`：禁止中文鼓励主句；每轮最多 1 处括号中文（`change_note = V17 en-primary tighten`）
- 同步 `docs/cet-tutor-design.md`；发版约定见 `docs/coding-standard.md` §2.2
