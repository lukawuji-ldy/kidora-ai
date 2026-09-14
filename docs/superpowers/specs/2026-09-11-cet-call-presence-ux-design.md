# CET 陪练通话在场感 UX（A+C）设计

- **日期：** 2026-09-11
- **仓库：** `kidora-ai`
- **状态：** Implemented（2026-09-11）
- **关联：** [ui-design.md](../../ui-design.md)、[cet-persona-design.md](../../cet-persona-design.md)、[2026-09-10-cet-tutor-zh-scaffold-tts-design.md](./2026-09-10-cet-tutor-zh-scaffold-tts-design.md)、[2026-09-11-cet-turn-latency-metrics-design.md](./2026-09-11-cet-turn-latency-metrics-design.md)、[2026-09-11-cet-persona-presence-polish-design.md](./2026-09-11-cet-persona-presence-polish-design.md)
- **后续升级：** 字母占位头像已由 [persona-presence-polish](./2026-09-11-cet-persona-presence-polish-design.md) 升级为真人静帧 + speaking 短视频与课题氛围；本规格状态机与贴纸约定仍有效。

## 1. 背景与目标

当前 `/cet/session/[id]` 是**聊天气泡墙 + 录音按钮**：外教「Emma」以长中英气泡出现，儿童缺少面对面一对一的在场感。

**目标（本期 A+C）：**

1. **A — 通话壳：** 首屏主视觉为人设大头像与听/想/说状态，对话降为字幕/紧凑历史，语音自动播放为主。
2. **C — 情绪表情/贴纸（仅此一项）：** 人设随回合情绪出现点头、笑、鼓掌等轻量反馈；**不**在本期改话术长度或中英呈现规则。

**方案 C 范围收窄（已确认）：**

| 原 C 设想 | 本期 |
|---|---|
| 人设表情/贴纸随情绪变化（点头、笑、鼓掌） | **实现**（见 §4） |
| Prompt 强制短回合 / 字数句数硬上限 | **保留不修改**：仍完全以既有 [中文脚手架规格](./2026-09-10-cet-tutor-zh-scaffold-tts-design.md) 为准；**不**新增 `V15` 短话术迁移、**不**追加 ≤60 字/≤3 句等硬上限 |
| 中英堆叠/括号注释呈现与 TTS 剥括号 | **保留不修改**：脚手架规格与现网 `speakableForTts` 行为不变 |

**非目标：**

- Live2D / 数字人 / 真视频通话 / 口型驱动
- 预合成「嗯/好的」占位 TTS（感知优化候选，见耗时规格；本期不做）
- 流式 TTS、并行 ASR/Safety、减 Safety 次数（属耗时优化，凭 [timing 规格](./2026-09-11-cet-turn-latency-metrics-design.md) 数据再开单）
- 改 Safety / Planner / Eval / Tutor Prompt 正文（本期 C 不碰 Prompt）
- 改 SSE 业务事件语义（除耗时规格另增的 `turn.timing`）

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 在场感形态 | 通话壳：静态人设头像 + CSS 状态环/轻动效 |
| 头像资源 | `personas.ts` 六人设；无图时用**稳定色板 + 首字母**占位；不引入新供应商 |
| 对话呈现 | 当前轮字幕为主；历史可折叠；气泡墙不再占首屏主面积 |
| 播放按钮 | 字幕/历史常驻「播放」可重听；失败/被拦时高亮「点这里听」 |
| 停麦反馈 | 立即进入 `thinking` + 头像点头轻动效；**不**播占位 TTS |
| 方案 C | **仅**情绪表情/贴纸；短话术与中英规则不改 |
| 情绪驱动 | **前端规则映射**（状态机 + 外教文案轻量启发式）；**不**新增后端 `emotion` 字段或 SSE |
| 打字入口 | 保留为次要折叠；主路径仍是录音 |

---

## 2. 信息架构与布局

### 2.1 儿童首屏结构（自上而下）

```
┌─────────────────────────────────────┐
│ Topbar: Kidora · CET    [结束练习]   │
│ planSummary（一行轻量提示，可保留）   │
├─────────────────────────────────────┤
│         PersonaStage（主视觉）        │
│  [大头像 + 状态环 + 人设名 + 贴纸槽]  │
│     状态文案：在听你说 / 想一想… / 在说 │
├─────────────────────────────────────┤
│ CaptionStrip（当前外教/孩子字幕）     │
│ [展开历史 ▾]（默认折叠）              │
├─────────────────────────────────────┤
│ 错误/温和提示（按需）                 │
│ [● 录音]     （主）                   │
│ [打字]       （次要折叠）              │
└─────────────────────────────────────┘
```

### 2.2 PersonaStage

- 数据：`GET /api/cet/sessions/{id}` 已有或可带的 `personaId`；展示名来自 [`kidora-web/src/lib/personas.ts`](../../../kidora-web/src/lib/personas.ts)。
- 视觉：圆形头像区域占首屏垂直中部；背景保持现有氛围渐变，**不**做成多卡片仪表盘。
- 贴纸槽：头像右上或下方固定一处，同时最多展示 **1** 个情绪贴纸（避免轰炸）。
- 占位映射（无图片时强制使用，保证六人设可区分）：

| persona_id | 展示色（CSS 变量建议） | 字母 |
|---|---|---|
| emma | `--persona-emma` 柔和青绿 | E |
| mike | `--persona-mike` 深蓝 | M |
| lily | `--persona-lily` 淡紫粉 | L |
| tom | `--persona-tom` 暖橙 | T |
| coco | `--persona-coco` 蜜桃 | C |
| alex | `--persona-alex` 青绿 | A |

实现期若增加 `public/personas/{id}.webp`，有图用图、无图回退上表；规格不强制本期必须出设计切图。情绪贴纸用 CSS/SVG 简图或现有静态资源，**不**引入新供应商 SDK。

### 2.3 CaptionStrip 与历史

- **当前字幕：** 外教最新完整句（随 `message.delta` 更新）；孩子侧在语音轮先显示「（语音）」，收到 `asr.transcript` 后替换。
- **历史：** 默认折叠为「刚才说了 N 轮」；展开后用紧凑行（角色标签 + **完整换行文案** + 「播放」重听），**不**恢复大气泡墙为主布局。
- 历史页 `/cet/history/[id]` 可保持只读列表；本期不强制改成通话壳。
- 字幕文案内容与括号注释规则：**不改**既有脚手架与 TTS 行为。

### 2.4 动效预算

至少下列有意动效（克制、可关眩晕）：

1. 状态环：`listening` 脉冲、`thinking` 慢转、`speaking` 声波动画。
2. 停麦瞬间：头像短促**点头**（CSS transform，不超过 400ms）——与 §4 `nod` 可共用同一动画实现。
3. 情绪贴纸：出现时短促缩放入场（不超过 300ms），停留后淡出（见 §4.3）。

禁止：多层级 glow、同时多个贴纸、首屏统计条、emoji 瀑布。

---

## 3. 前端状态机

状态由录音与 SSE/播放驱动，**不**新增后端状态字段。

| 状态 | 进入条件 | UI |
|---|---|---|
| `idle` | 可交互且未录音、未等回复、未播放 | 「轮到你说」；录音可点 |
| `listening` | `WavRecorder.start` 成功 | 「在听你说」；停麦结束录音 |
| `thinking` | 停麦并已发起 `/stream`，或文本发送后 | 「想一想…」；录音禁用 |
| `speaking` | 外教音频 `play()` 开始（或仅文本路径字幕完成且无 TTS 时短亮后回 `idle`） | 「外教在说」；录音禁用至播放结束 |
| `blocked` | `safety` / 硬错误 | 温和文案；允许重新开麦或结束 |

### 3.1 转移要点

1. `listening` → `thinking`：`stop()` 成功且准备 `streamTurn` 时立即切换（先于网络返回）；触发一次 `nod`（听清/收到）。
2. `thinking` → `speaking`：首次成功开始播放 `audio.tts`（或浏览器兜底开播）。
3. `speaking` → `idle`：`ended` / 播放失败且已展示「点这里听」。
4. 开场 `opening=true`：进入页后 `thinking` →（TTS）`speaking` → `idle`。
5. 自动播放被浏览器拦截：留在可点「点这里听」；点按后进入 `speaking`。

### 3.2 与现有 busy 关系

现有 `busy` 可与状态机合并或由状态派生：`busy = listening | thinking | speaking`（`blocked` 下按错误是否可重试决定）。规格要求实现期去掉「整页无状态反馈、只转圈」的体验。

---

## 4. 方案 C — 人设情绪表情 / 贴纸

### 4.1 情绪枚举（锁定）

| id | 表现 | 典型触发 |
|---|---|---|
| `nod` | 点头 | 停麦进入 `thinking`；开场流开始 |
| `smile` | 笑 | 外教本轮文案含鼓励类信号（见 §4.2）且进入 `speaking` |
| `clap` | 鼓掌 | 外教本轮文案含强表扬/完成类信号且进入 `speaking` |
| `neutral` | 无贴纸 | 默认；`blocked`；或启发式未命中 |

同时只展示一个；优先级：`clap` > `smile` > `nod` > `neutral`（同一时刻取最高）。

### 4.2 启发式映射（前端，无后端字段）

对**当前轮**外教累计文本（`message.delta` 拼满后的全文，大小写不敏感）做关键词命中（中英均可）：

| 情绪 | 命中任一即成立（实现期可微调词表，但须保持「轻量、可单测」） |
|---|---|
| `clap` | `太棒`、`真棒`、`了不起`、`great job`、`well done`、`amazing`、`高五`、`鼓掌` |
| `smile` | `不错`、`很好`、`加油`、`试得很好`、`good try`、`great try`、`nice`、`👍`（若文案含） |

- 若同时命中 `clap` 与 `smile` → 只播 `clap`。
- 未命中 → `speaking` 阶段可不贴纸，或仅保留状态环；停麦的 `nod` 仍按状态机触发。
- **不**根据发音分（儿童主界面不展示）驱动情绪。
- **不**解析 Safety 策略名；`blocked` 固定 `neutral`。

### 4.3 时序

| 情绪 | 开始 | 结束 |
|---|---|---|
| `nod` | `listening`→`thinking` 当帧 | 动画结束即结束（≤400ms）；不占贴纸槽长时间 |
| `smile` / `clap` | 进入 `speaking` 且启发式命中 | 贴纸展示 **1.2～1.8s** 后淡出；播放结束若仍显示则一并清除 |
| `neutral` | — | 清空贴纸槽 |

`nod` 以头像 transform 为主；`smile`/`clap` 以贴纸槽图形为主（可叠加极轻头像表情，但不得遮挡状态文案）。

### 4.4 无障碍与克制

- 贴纸纯装饰：`aria-hidden="true"`，不替代状态文案。
- 尊重「减少动效」系统偏好时：仅切静态贴纸一帧或跳过动画，仍可短暂显示图标。
- 禁止每 token 抖动；仅在状态切换或「全文启发式 + speaking」时触发一次。

---

## 5. 实现落点（实现期；本设计阶段不改代码）

| 层 | 路径 |
|---|---|
| 陪练页 | [`kidora-web/src/app/cet/session/[id]/page.tsx`](../../../kidora-web/src/app/cet/session/[id]/page.tsx) |
| 样式 | [`kidora-web/src/app/globals.css`](../../../kidora-web/src/app/globals.css) |
| 人设常量 | [`kidora-web/src/lib/personas.ts`](../../../kidora-web/src/lib/personas.ts)（`color` / `initial` / `personaById`） |
| 情绪词表/映射 | [`kidora-web/src/lib/personaEmotions.ts`](../../../kidora-web/src/lib/personaEmotions.ts) |
| Prompt / Flyway | **未改**（无短话术迁移） |
| 后端 API | **无协议变更**（本规格）；耗时上报见并行规格 |

前端模型选用：实现阶段按 `AGENTS.md` 对 `kidora-web` 使用指定前端模型。

---

## 6. 验收标准

1. 首屏主焦点是人设头像与听/说状态，而非气泡列表；折叠历史后主区仍可读作「通话」。
2. 停麦进入 `thinking` 时可见一次点头反馈。
3. 外教回复含鼓励/强表扬样例文案时，进入 `speaking` 分别出现笑 / 鼓掌贴纸，且同时最多一个；结束后淡出。
4. TTS 自动播放成功时，儿童完成一轮无需点击「播放」即可听完；「播放」仍常驻便于重听；失败/拦截时高亮「点这里听」。
5. 状态机五态可在真机走通：开场 → 说 → 听想说 → 可再开麦。
6. **回归：** Tutor Prompt、脚手架中英规则、TTS 剥括号行为与改前一致（本规格未改后端话术）。
7. 打字次要入口仍可用；结束练习与报告路径不变。

---

## 7. 文档同步（实现交付时必须同改）

| 文档 | 章节 |
|---|---|
| [`docs/ui-design.md`](../../ui-design.md) | §5 CET 陪练主界面：通话壳 + 状态机 + 字幕 + 情绪贴纸 |
| [`docs/cet-persona-design.md`](../../cet-persona-design.md) | §4 UI：陪练页大头像在场与情绪贴纸约定 |
| [`docs/cet-lesson-flow.md`](../../cet-lesson-flow.md) | 儿童侧交互说明（气泡→字幕/通话态；注明情绪为前端装饰） |
| 本规格 | 状态改为 Implemented，并注明实现日期 |

---

## 8. 与耗时规格的边界

- 本规格改善**感知在场**与**情绪反馈**；不替代分段耗时埋点。
- **不再**承诺通过本规格缩短 `ttsMs`（短话术不在本期 C）；耗时分析勿把「A+C 上线」自动等同于回复变短。
- 不把「埋点完成」当作本规格验收。
