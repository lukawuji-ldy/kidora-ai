# CET AI 外教人设（Persona）

通过不同导师个性提升儿童学习趣味性。选型进入 Planner 上下文与 Tutor 系统提示（配置化 Prompt，禁止代码硬编码长文）。

---

## 1. 人设一览

| ID | 名称 | 特点 | 适合 |
|---|---|---|---|
| `emma` | Emma | 温柔鼓励型 | 低龄、怯场 |
| `mike` | Mike | 探险游戏型 | 好动、喜欢任务 |
| `lily` | Lily | 故事型 | 喜欢叙事、动物/奇幻 |
| `tom` | Tom | 挑战型 | 需要一点难度刺激 |
| `coco` | Coco | 萌宠型 | 低龄、宠物主题 |
| `alex` | Alex | 游戏任务型 | 积分/关卡动机 |

（展示名可本地化；**稳定 id** 用于库与 API。）

---

## 2. 选型规则

1. 用户显式选择 > Profile `preferredPersonaIds` > 主题启发（如宠物→`coco`/`lily`）> 默认 `emma`。
2. Safety 与教学边界**高于**人设；任何 persona 不得越界。
3. Re-plan 默认**不更换**人设，除非家长设置或孩子明确要求。

---

## 3. Prompt 配置约定

- `prompt_group = CET`
- 代码键示例：`cet.persona.emma.system`、`cet.tutor.user`、`cet.planner.system`
- 人设差异：语气、比喻、任务包装；**共享**教学目标与 Safety 附录。

---

## 4. UI

- `kidora-web` 人设卡：idle 半身肖像（无图回退色板/首字母）、一句话特点。
- **陪练页通话壳：** 首屏主视觉为更大半身像（手势 + 场景背景）+ 听/想/说状态环；`speaking` 播短循环静音视频；顶部主题芯片；对话为更大字号字幕气泡。
- **情绪贴纸（前端装饰）：** 点头 / 笑 / 鼓掌由状态机 + 外教文案关键词启发式驱动；无后端 `emotion` 字段或 SSE；贴纸 `aria-hidden`，不替代状态文案。
- **课题氛围：** 前端按 `topic`（可回退 planSummary）映射主题包；宠物主题强化装饰与背景色。
- 静态资源：`public/personas/{persona_id}/{state}.webp`、`speaking.webm`；详见 [2026-09-11-cet-persona-presence-polish-design.md](superpowers/specs/2026-09-11-cet-persona-presence-polish-design.md)。

详见 [ui-design.md](ui-design.md)、[2026-09-11-cet-call-presence-ux-design.md](superpowers/specs/2026-09-11-cet-call-presence-ux-design.md)。

---

## 5. 数据

- `cet_lesson_session.persona_id`
- Profile 可累计偏好计数（MVP-3）
- **人设 ↔ TTS 音色**：表 `cet_persona_voice`（`UNIQUE (persona_id, vendor_code)`）

### 5.1 腾讯 TTS 音色映射（seed）

官方音色列表：[腾讯云语音合成音色列表](https://cloud.tencent.com/document/product/1073/92668)

| persona_id | voice_id | voice_name | voice_traits |
|---|---|---|---|
| emma | 501009 | WeWinny | 外语女声；英文清晰温和，贴合温柔鼓励型外教 |
| mike | 501008 | WeJames | 外语男声；英文沉稳有力，贴合探险任务型 |
| lily | 603004 | 温柔小柠 | 中英聊天女声；柔和、偏叙事，贴合故事型 |
| tom | 101050 | WeJack | 精品英文男声；干脆有力，贴合挑战型 |
| coco | 502007 | 智小虎 | 中英聊天童声；活泼亲切，贴合萌宠/低龄 |
| alex | 603000 | 懂事少年 | 中英特色男声；少年感，贴合游戏关卡型 |

### 5.2 讯飞 TTS 音色映射（seed）

对照：[讯飞开放平台 TTS 控制台](https://console.xfyun.cn/services/tts)（`voice_id` = `vcn`；locale 与腾讯一致 `en-US`）

| persona_id | voice_id | voice_name | voice_traits |
|---|---|---|---|
| emma | x4_xiaoyan | 讯飞小燕 | 普通话女声；清晰温和，贴合温柔鼓励型外教 |
| mike | aisjiuxu | 讯飞许久 | 普通话男声；沉稳，贴合探险任务型（基础发音人唯一男声） |
| lily | x4_yezi | 讯飞小露 | 普通话女声；柔和，贴合故事型 |
| tom | aisjiuxu | 讯飞许久 | 普通话男声；与 mike 暂共用 vcn |
| coco | aisbabyxu | 讯飞许小宝 | 普通话童声；活泼亲切，贴合萌宠/低龄 |
| alex | aisjinger | 讯飞小婧 | 普通话女声；年轻感；无少年男声时作游戏型替代 |

运行时：`session.persona_id` + `speech_route.primary_vendor` → `cet_persona_voice.voice_id` → MCP `tts_synthesize.voice`。无 ACTIVE 映射时 `voice=null`（厂商默认：腾讯 `101001`、讯飞 `x4_xiaoyan`）。管理台可改映射，见旁路 manage「人设音色」页。

---

## 6. MVP

| 能力 | MVP |
|---|---|
| 6 人设配置 + 手动选择 | 1 |
| 人设 → 腾讯 TTS VoiceType 映射 | 2C+ |
| 人设 → 讯飞 TTS vcn 映射 | 2C+ |
| 基于 Profile 推荐 | 3 |
| 更多人设运营可配 | 4（管理台） |
