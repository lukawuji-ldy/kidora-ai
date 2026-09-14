# CET 陪练中文脚手架 + TTS 括号不读设计

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`
- **状态：** Superseded（练习轮语言口径）→ [2026-09-11-cet-en-primary-scaffold-design.md](./2026-09-11-cet-en-primary-scaffold-design.md)；**TTS 剥括号规则仍有效**
- **关联：** `cet.tutor.system` / `cet.tutor.opening.system`；`CetLessonService.speakableForTts`；既有 V9/V10 对话 UX 与 TTS 迁移

## 1. 背景与目标

当前 Tutor 倾向「英文主句 + 括号中文翻译」，低龄孩子在求助/纠错时阅读负担大。示例问题轮：

> 孩子：我的狗的颜色，我不知道用英语怎么说？  
> 助教：Great try! (不错哦！) You said "The..." — are you thinking about the color?(你是想说颜色吗？) You can say "My dog is white" or "Mydog is black." Let's try again: What color is your dog? (你的狗是什么颜色？)

期望改为：**中文搭脚手架 + 英文示范句**；若英文后仍带括号中文注释，**气泡保留、语音不读**。

**目标：**

1. 在约定场景下，鼓励/讲解/引导/追问用中文，示范句与关键点用英文。
2. 英文句子后可保留 `(中文注释)` 供屏幕阅读。
3. TTS 朗读中文正文 + 英文例句，**不朗读**含中文的括号注释。
4. 不改 SSE 协议、不新增表、不做结构化 JSON 输出（方案 A）。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 方案 | A：Prompt 改写 + TTS 口径对齐（剥括号，读中英正文） |
| 中文脚手架触发 | A0/A1 **默认**；孩子**中文求助**；**显式纠错** |
| A2+ 平常练习 | 仍以英文为主；仅求助/显式纠错切脚手架 |
| 气泡括号 | 英文后的 `(中文注释)` **保留** |
| TTS | 中英正文都读；括号注释不读 |
| A0/A1 下一问 | 默认中文（与用户举例一致）；必要时英文问句可带括号注释 |

### 1.2 非目标

- 不做 `zhGuide` / `enExamples` 结构化字段与前端重拼装
- 不改 Safety / Planner / Eval 主逻辑
- 不新增 DB 表；仅 Prompt Flyway 更新 + 代码注释/单测说明
- 不强制所有 CEFR 全程中文引导

## 2. 话术规则

### 2.1 触发中文脚手架

同时满足任一即启用（优先级高于「A2+ 英文为主」）：

1. 当前计划 CEFR 为 **A0 或 A1**
2. 孩子本轮（或近期）用**中文求助**如何用英语表达
3. 本轮进入**显式纠错**（目标语法反复出错或严重影响理解；模板仍：短鼓励 → 中文讲解 → 英文关键点 → 请再说一次）

### 2.2 表达约束

1. 鼓励、讲解、引导、追问：**中文**
2. 示范句 / 关键点：**英文**（短句，可给 1～2 个对照，如 `My dog is white` / `My dog is black`）
3. 若输出英文句子：其后**可以**附 `(中文注释)`，气泡完整保留
4. **禁止**整段「英文主句 + 逐句括号翻译」堆叠作为默认形态
5. 每轮仍：先鼓励 → 按需纠错 → **只提一个**下一问；禁止长篇讲课

### 2.3 目标气泡示例

> 说得不错！你是想说颜色吗？你可以说 "My dog is white" 或 "My dog is black"。我们再试一次：你的狗是什么颜色？

可选英文问句形态：

> What color is your dog? (你的狗是什么颜色？)

## 3. TTS 与发音评测

| 层 | 行为 |
|---|---|
| 气泡 `finalText` | 原文完整保留（含 `(中文注释)`） |
| TTS 朗读稿 | `speakableForTts(finalText)`：去掉含中文的 `(...)` / `（...）`，保留中文主句 + 英文例句 |
| 发音评测参考 | 优先英文示范句/目标跟读句；不把整段中文讲解当 `referenceText` |

**朗读示例**

- 气泡：`说得不错！你可以说 "My dog is white"。What color is your dog? (你的狗是什么颜色？)`
- TTS：`说得不错！你可以说 "My dog is white"。What color is your dog?`

**实现说明：** 现有 `CetLessonService.speakableForTts` 正则已剥含汉字的括号并保留其余正文，行为与本节一致。已落地：

- 方法 / `buildSpeechExtras` 的 Javadoc（废弃「TTS 只读英文主句」口径）
- V12 Prompt 迁移与相关文档（含 V10 过时表述清理）
- 单测：覆盖「中文脚手架 + 英文例句 + 括号注释」→ TTS 不含括号、保留中文与英文

## 4. Prompt 变更

新增 Flyway 迁移（建议 `V12__cet_tutor_zh_scaffold.sql`），更新：

- `cet.tutor.system`
- `cet.tutor.opening.system`

并同步当前 `published_version` 对应的 `prompt_template_version` 行（与 V9/V10 写法一致）。

要点写入系统提示（中文正文）：

1. 中文脚手架触发条件（§2.1）
2. 表达约束（§2.2）
3. 括号注释仅供屏幕阅读；朗读由系统剥括号（勿依赖模型自己「不写括号」）
4. 开场：A0/A1 可用中文打招呼/点题 + 一个简单下一问（中文或带注释的英文问句）；示范词汇仍可英文点出

`cet.tutor.user` / opening.user 可保持结构，必要时加一句「按系统脚手架规则回复」。

## 5. 文档同步（实现交付时）

同一次交付更新：

- `docs/cet-tutor-design.md`（小循环中英比例 / 纠错话术）
- `docs/cet-lesson-flow.md`（若有气泡/TTS 说明）
- 本规格状态改为 Accepted（实现完成后可再标 Implemented）

## 6. 验收标准

1. A0/A1 或纠错/中文求助：气泡以中文脚手架 + 英文示范为主，而非英文+括号翻译堆叠。
2. 存在 `(中文注释)` 时：气泡仍有括号；TTS 文本不含括号内容。
3. 无括号的中文引导会出现在 TTS 朗读稿中。
4. A2+ 非求助、非显式纠错：仍以英文为主（回归既有体验）。
5. 相关单元测试通过（`speakableForTts` + 既有 CetLessonService 语音附加测试）。

## 7. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 模型仍输出大量英文+括号 | Prompt 明确禁止堆叠；验收用样例回归 |
| A0/A1 沉浸感下降 | 示范句/关键点强制英文；A2+ 平常仍英文为主 |
| 腾讯等人设音色偏「外语声」读中文效果一般 | 已有中英音色映射（lily/coco 等）；不在本期改映射表 |
| 发音评测误用中文讲解作 reference | 保持现有 reference 选取逻辑，仅对英文目标句评分 |
