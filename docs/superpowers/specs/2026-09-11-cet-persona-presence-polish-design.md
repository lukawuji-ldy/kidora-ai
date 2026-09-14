# CET 外教真人肖像 + 课题氛围整页美化（方案 C）设计

- **日期：** 2026-09-11
- **仓库：** `kidora-ai`
- **状态：** Implemented（2026-09-11）
- **关联：** [ui-design.md](../../ui-design.md)、[cet-persona-design.md](../../cet-persona-design.md)、[2026-09-11-cet-call-presence-ux-design.md](./2026-09-11-cet-call-presence-ux-design.md)

## 1. 背景与目标

通话壳 A+C（[call-presence 规格](./2026-09-11-cet-call-presence-ux-design.md)）已落地：五态状态机、状态环、点头/笑/鼓掌贴纸、字幕条。但主视觉仍是**色板 + 首字母**，儿童难以感到「真的在和外教对话」；整页观感偏平、课题氛围缺失。

**本期目标：**

1. 六人设换成**真人感肖像**；首屏头像更大，通话感更强。
2. **方案 C 素材策略：** 静帧为主；**仅** `speaking` 使用短循环静音视频；失败/减动效回退静帧。
3. **整页视觉升级** + 按课题 `topic` 切换氛围主题包（宠物等）。
4. 开课页人设卡同步展示 idle 肖像。

**非目标：**

- 实时数字人 / Live2D / TTS 口型驱动 / 新供应商 SDK
- 后端新增 `emotion`、`theme` 字段或 SSE
- 改 Tutor / Planner / Safety Prompt、脚手架中英规则、TTS 剥括号
- 改 SSE 业务事件语义
- 按课题动态生成 AI 头像（肖像资产入库静态资源，主题只换背景氛围）

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 头像形态 | 多表情静帧 + 仅 speaking 短循环静音视频（方案 C） |
| 视觉范围 | 整页通话感重做 + 课题主题氛围 |
| 主题包 | 四套：`pets` / `colors` / `food` / `default`；由前端从 `topic` 关键词映射 |
| 情绪驱动 | 沿用既有前端状态机 + `inferSpeakingEmotion`；不新增后端字段 |
| 无图回退 | 现有色板 + 首字母 |
| 信息架构 | 保持通话壳自上而下结构；不恢复大气泡墙为主布局 |

---

## 2. 素材与路径约定

### 2.1 人设肖像

路径：`kidora-web/public/personas/{personaId}/`

| 文件 | 类型 | 用途 |
|---|---|---|
| `idle.webp` | 静帧 | 默认可交互、开课人设卡 |
| `listening.webp` | 静帧 | `callState === listening` |
| `thinking.webp` | 静帧 | `callState === thinking` |
| `smile.webp` | 静帧 | 鼓励笑（可与 speaking 贴纸叠加时优先用静帧表情，见 §3） |
| `clap.webp` | 静帧 | 强表扬 |
| `speaking.webp` | 静帧 | speaking 视频失败或减动效时的回退 |
| `speaking.webm` | 短循环静音视频 | `callState === speaking` 主路径 |

约束：

- 六人设 id：`emma` | `mike` | `lily` | `tom` | `coco` | `alex`（与 [`personas.ts`](../../../kidora-web/src/lib/personas.ts) 一致）。
- 肖像风格（硬性）：**半身像（约腰部以上）**，双手/手势可见，身后有教室/场景背景；看起来像真实外教照片/短视频，避免塑料感、过度磨皮等「一眼 AI」观感；表情**生动、活泼、逼真**，鼓励/说话态允许略夸张，仍须适合儿童产品。
- 六人设面容与发型可区分，气质匹配（Emma 温和、Mike 活力、Lily 故事感、Tom 挑战感、Coco 萌宠亲和、Alex 少年关卡感）。
- 视频：静音、循环约 3s、半身构图与静帧对齐；说话循环由多口型帧（闭/半开/开/元音）按不规则音节节奏合成，并带轻微头动，避免两帧硬切翻页感。
- 主题可见性：陪练页展示主题芯片；`pets` 等主题 motif 与背景色需足够可辨；`topic` 空时可回退用 `planSummary` 关键词。
- 实现期可用摄影素材或高质量生成后精选入库；规格不绑定具体工具。缺任一文件时该态回退：优先同人设 `idle.webp`，再回退色板+字母。

### 2.2 课题主题

路径：`kidora-web/public/themes/{themeId}/motif.svg`（轻量装饰剪影；氛围色以 CSS 变量为主，不依赖大图背景）。

| themeId | 触发关键词（中英，大小写不敏感，命中任一词） | 视觉方向 |
|---|---|---|
| `pets` | 宠物、狗、猫、鱼、鸟、仓鼠、pet、dog、cat、fish、bird、hamster | 暖青绿；轻量爪印/剪影，不抢头像 |
| `colors` | 颜色、色彩、红、蓝、绿、黄、color、colours、red、blue、green、yellow | 柔和多色块氛围 |
| `food` | 食物、水果、吃、早餐、food、fruit、eat、breakfast、apple | 暖橙氛围 |
| `default` | 未命中上述 | 升级版通话壳渐变（非字母时代的平淡青绿） |

映射函数：`themeFromTopic(topic: string): ThemeId`，优先级 `pets` > `colors` > `food` > `default`（同一 topic 多命中时取最高优先级）。数据源：会话已有 `topic`（`GET /api/cet/sessions/{id}` / 开课上下文）；**不**解析 `planSummary` 全文做主题（避免摘要噪声）。

---

## 3. PersonaStage 行为

沿用五态：`idle` | `listening` | `thinking` | `speaking` | `blocked`。

| 状态 / 情绪 | 主媒介 | 叠加 |
|---|---|---|
| `idle` | `idle.webp` | 状态环静息 |
| `listening` | `listening.webp` | 脉冲环；文案「在听你说」 |
| `thinking` | `thinking.webp` | 慢转环；停麦/开场 `nod` 点头 ≤400ms |
| `speaking` | `speaking.webm`（循环）；失败→`speaking.webp` | 声波环；文案「外教在说」 |
| `speaking` + smile/clap | **仍播 speaking 视频**；贴纸槽展示 smile/clap（与 A+C 一致） | 不因情绪切走视频轨，避免播放抖动；`smile.webp`/`clap.webp` 供贴纸或后续扩展，本期不在 speaking 中切主头像 |
| `speaking` → `idle` | 播放结束后直接 `idle.webp` | 不插入额外笑/鼓掌静帧停留 |
| `blocked` | `idle.webp` 或色板回退 | `neutral`；温和文案 |

视频元素要求：`muted` `playsInline` `loop` `autoPlay`；`aria-hidden="true"`（状态文案仍由 `.persona-status` 承担）。`prefers-reduced-motion: reduce` 时**禁止**自动播视频，直接用 `speaking.webp`。

头像尺寸：PersonaStage 使用**半身竖卡**（约 3:4），明显大于旧圆形头像；圆角矩形遮罩保留场景背景与手势；姓名 + 状态文案仍在头像下方；顶部展示主题芯片。

---

## 4. 整页视觉（通话感）

信息架构不变（Topbar → PersonaStage → CaptionStrip → Call panel），升级观感：

1. **Shell：** `data-theme={themeId}` 驱动 CSS 变量（背景渐变、强调色、录音按钮色）；主题插画作为底层装饰，禁止首屏卡片墙/统计条。
2. **CaptionStrip：** 半透明字幕条、更紧凑；历史默认折叠。
3. **录音主按钮：** 更接近通话控键（圆形主按钮）；打字入口仍次要折叠。
4. **排版：** 儿童可读字号与间距；避免紫白 AI 模板风、多层级 glow、emoji 瀑布。
5. **开课页：** [`cet/page.tsx`](../../../kidora-web/src/app/cet/page.tsx) 人设卡展示 `idle.webp`，无图回退字母。

动效预算（至少）：状态环、点头、speaking 视频、贴纸入场、主题背景极轻浮动（减动效时全部关掉视频与浮动）。

---

## 5. 前端模块落点

| 模块 | 路径 | 职责 |
|---|---|---|
| 陪练页 | [`kidora-web/src/app/cet/session/[id]/page.tsx`](../../../kidora-web/src/app/cet/session/[id]/page.tsx) | 接主题 class；把头像区交给 PersonaStage |
| PersonaStage | `kidora-web/src/components/cet/PersonaStage.tsx`（新建） | 静帧/视频切换、贴纸、状态文案、减动效回退 |
| 人设常量 | [`kidora-web/src/lib/personas.ts`](../../../kidora-web/src/lib/personas.ts) | 增加资源 URL 辅助（如 `personaAsset(id, state)`） |
| 主题映射 | `kidora-web/src/lib/lessonThemes.ts`（新建） | `themeFromTopic` + 词表；可单测 |
| 情绪 | 既有 [`personaEmotions.ts`](../../../kidora-web/src/lib/personaEmotions.ts) | 不改协议；词表可微调 |
| 样式 | [`globals.css`](../../../kidora-web/src/app/globals.css) | 主题变量、放大头像、字幕条、通话控键 |
| 开课页 | [`cet/page.tsx`](../../../kidora-web/src/app/cet/page.tsx) | 人设卡 idle 图 |
| 静态资源 | `public/personas/**`、`public/themes/**` | 见 §2 |

**后端：** 无 API / DDL / Prompt 变更。`topic` 与 `personaId` 已足够。

前端实现模型：按 `AGENTS.md`，`kidora-web` 使用指定前端模型。

---

## 6. 验收标准

1. 有素材时，陪练首屏主焦点为**大尺寸真人肖像**，而非字母「E」等。
2. `listening` / `thinking` / `idle` 切对应静帧；`speaking` 播循环视频；视频失败或减动效时显示 `speaking.webp`，不白屏、不卡死状态机。
3. 鼓励/强表扬文案进入 speaking 时贴纸仍出现（与 A+C 一致）；同时最多一个贴纸。
4. `topic` 含宠物等关键词时 `data-theme=pets`（及对应氛围）；未命中为 `default`。
5. 开课页六人设卡可见 idle 肖像（有资源时）。
6. 录音 → thinking → speaking → idle 全链路与结束练习/报告/历史折叠行为相对 A+C **不回归**。
7. 无障碍：头像/视频装饰性；状态依赖可见文案；尊重 `prefers-reduced-motion`。

---

## 7. 文档同步（实现交付时同改）

| 文档 | 章节 |
|---|---|
| [`docs/ui-design.md`](../../ui-design.md) | §5：真人肖像方案 C、主题包、放大 PersonaStage |
| [`docs/cet-persona-design.md`](../../cet-persona-design.md) | §4 UI：静帧/speaking 视频路径与回退 |
| [`docs/cet-lesson-flow.md`](../../cet-lesson-flow.md) | 儿童侧：课题氛围为前端装饰 |
| [call-presence 规格](./2026-09-11-cet-call-presence-ux-design.md) | 注明头像由字母占位升级为本规格；本规格不推翻其状态机 |
| 本规格 | 实现完成后将状态改为 Implemented 并注明日期 |

---

## 8. 与既有规格边界

- **继承** call-presence 的五态状态机、字幕条、情绪贴纸启发式、语音优先。
- **升级** 头像媒介（字母 → 静帧 + speaking 视频）与整页/主题视觉。
- **不**承担耗时优化（见 [timing 规格](./2026-09-11-cet-turn-latency-metrics-design.md)）；speaking 视频仅为感知在场，不缩短 `ttsMs`。
