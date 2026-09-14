# 前端 UI 与交互设计（kidora-web）

`kidora-web`（Next.js）为 Kidora 统一前台壳；CET 为产品路由之一。  
管理 UI 见旁路 `kidora-admin-web`，不在本仓库。

---

## 1. 设计原则

1. 儿童主路径：少字、大按钮、强反馈、避免信息过载。
2. 家长路径：报告、设置、学习者档案；与儿童会话 UI 分区。
3. 可解释性：工具/思考面板在家长或调试模式可用；儿童模式默认隐藏复杂面板。
4. 品牌：Kidora；CET 产品名 Child English Tutor。

---

## 2. 工程划分

| 工程 | 技术 | 职责 |
|---|---|---|
| `kidora-web` | Next.js | 用户前台 |
| `kidora-admin-web` | Vue3（旁路） | 运营后台 |

开发态代理：

- `/api/*`（非 cet）→ `kidora-agent-server`
- `/api/cet/*` → `cet-tutor-server`

---

## 3. 信息架构

```
/login
/register             # 家长账号 + 首个儿童（昵称 / 英语水平）
/home                 # 产品入口（CET 卡片 + 未来其它）；顶栏进个人中心
/settings             # 个人中心：家长昵称/改密 + 儿童增改/软删除
/cet                  # 选主题 / 人设 / 学习者 + 上课记录列表
/cet/session/[id]     # 陪练主界面（支持断点续课 hydration）
/cet/history/[id]     # 课时只读回看（文本 + 外教按需重 TTS）
/cet/report/[id]      # 会话报告
/chat                 # 通用助手（平台预留）
```

---

## 4. 登录与注册

- User JWT；刷新策略实现期定。
- `/register`：一次提交用户名、密码、儿童昵称、英语水平（启蒙/初级/中级/进阶 → 后端映射 CEFR）；成功发 JWT 进 `/home`。见 [2026-09-14-cet-web-register-learner-design.md](superpowers/specs/2026-09-14-cet-web-register-learner-design.md)。
- `/settings` 个人中心：改家长昵称 / 密码；儿童档案列表、添加、编辑昵称与英语水平、软删除（至少保留一名）。见 [2026-09-14-web-personal-center-design.md](superpowers/specs/2026-09-14-web-personal-center-design.md)。
- 儿童使用可走家长监护下的 learner 切换，不单独弱密码儿童账号（默认策略；合规细化 MVP-4）。

---

## 5. CET 陪练主界面（要点）

**首屏（儿童模式）为通话壳：**

- **沉浸式大舞台（视频通话感）：** 半身像占舞台主体；姓名/状态叠在画面上；字幕默认 2 行可「全文」展开；录音为底部悬浮胶囊；课题说明顶栏可折叠。见 [2026-09-11-cet-persona-immersive-stage-design.md](superpowers/specs/2026-09-11-cet-persona-immersive-stage-design.md)
- 顶栏 **三目标递进**（`childGoals` 1/3·2/3·3/3，默认折叠）+ 轻量 `planSummary`；见 [2026-09-11-cet-lesson-goals-props-autolisten-design.md](superpowers/specs/2026-09-11-cet-lesson-goals-props-autolisten-design.md)
- **PersonaStage（主视觉）：** 大半身像（手势可见、场景背景）+ 圆角竖卡；`speaking` 短循环视频，其它态表情静帧；无资源时色板+首字母；主题芯片叠在画面上。
- **教具舞台（后端权威 + SSE 实时下发）：** 展示哪张图由后端 `PropStageDirector` 判定，每轮通过 SSE `turn.prop` 事件下发；前端只渲染，不做任何字幕正则、颜色线索或主题特判。主图取外教自己声明的 `[[PROP:词]]`，声明缺失时退回按字幕点名顺序 → **左右分屏**（`propFocus`，约 38%/62%，最多 3 张，活跃图为第一个）；既无声明也没点名 → 人像主视觉（`personaFocus`）。`blocked` 一律收起分屏。开课返回的 `session.propAssets` 只用于预热图片缓存，不决定首帧布局。取图失败退 `/props/default/star.svg` 占位（**整屏都是星星通常意味着道具目录没对上，查 `cet-tutor-server` 启动日志里的道具根目录**）。只读图 `GET /api/cet/props/{lemma}`（JWT，带 ETag/304）。见 [2026-09-14-cet-prop-declared-lemma-design.md](superpowers/specs/2026-09-14-cet-prop-declared-lemma-design.md)、[2026-09-14-cet-prop-realtime-stage-design.md](superpowers/specs/2026-09-14-cet-prop-realtime-stage-design.md)
- **课题氛围：** 由会话 `topic`（可回退 `planSummary`）前端映射 `pets|colors|food|default`，只影响背景色与主题芯片，不参与选图。
- **素材署名页 `/legal/credits`：** 读 `GET /api/cet/props/credits`（JWT），列出 `attribution_required = TRUE` 的素材来源、作者与许可协议。引入 CC-BY / CC-BY-SA 素材时必须保持该页可访问。
- 当前字幕：字幕条形态、角色胶囊；默认两行；见 [2026-09-11-cet-persona-presence-polish-design.md](superpowers/specs/2026-09-11-cet-persona-presence-polish-design.md)
- **情绪贴纸（前端装饰）：** 停麦进入 `thinking` 时点头；外教文案启发式命中鼓励/表扬时在 `speaking` 出现笑/鼓掌贴纸（同时最多 1 个；无后端 emotion 字段）
- **CaptionStrip：** 当前外教/孩子字幕为主；点「查看对话历史 · N 轮」展开底部抽屉（顶栏固定「收起」）；展开时暂隐当前字幕以免挡脸；孩子本场原音可回放（内存，不落库）
- 进入页后自动拉取外教开场（TTS）；字幕与历史行常驻「播放」：外教可重听 TTS，孩子语音轮可回放本机录音；自动播被拦时「播放」高亮为「点这里听」
- **历史展开：** 底部叠层抽屉内完整换行可读；背后人像仍大面积可见；不整页滚动
- **语音优先 / 自动听：** 默认外教 TTS **播完并按文案估算补齐**后再自动开麦（`localStorage cet.autoListen`，可关）；播放中禁止开麦掐尾句；聆听中主按钮「停止并发送」为提前收尾；关闭后回退点「录音」+ 手动停发；「打字」为次要入口；悬浮胶囊 dock
- 孩子侧字幕：语音轮先「（语音）」，收到 `asr.transcript` 后替换；录音仅浏览器内存，不落库
- 录音：`WavRecorder` + 本地 VAD（`vadGate`：起说 RMS≈0.0035、有效响亮≥280ms 后尾静音≈850ms 才停麦；裁切峰值远低于起说阈值，避免轻声被裁没误报）；强制 `AudioContext.resume`；近静音 / 未起说在前端拦截，不上传 ASR；录音已拆除时提示「录音已结束…」而非「尚未开始录音」
- **顶栏：** **暂停**（离开页面，保持 `PRACTICING` 可续课）与 **结束并总结**（立刻 `POST .../complete`，不说再见）。阶段评测 `complete` 后自动进入告别：外教再见 → 孩子回 → 外教再鼓励 → SSE `session.completed` 跳小结；45s 无回应可走 `wrapUpTimeout`。

**不在首屏堆：** 复杂统计、完整计划树、工具原始 JSON、发音四维分数、**分段耗时数字**（`turn.timing` / client-timing 仅联调/落库；儿童 UI 静默上报 `e2eHeardMs`）。

家长/调试抽屉可看：当前计划目标、阶段、评测分数。

---

## 6. 消息与流式

- SSE 消费 [agent-flow.md](agent-flow.md) / CET 事件（含 `opening=true` 开场流；语音轮消费 `asr.transcript`；`turn.timing` 服务端分段耗时，儿童不展示）。
- 开播成功后静默 `POST .../turns/{turnIndex}/client-timing`（`e2eHeardMs`），失败最多重试 1 次，不影响陪练。
- 儿童侧：优先展示 Tutor 话语与鼓励；`cet.assessment` 转为友好文案。
- `safety.block`：温和提示换主题/重说，不展示内部策略名。

---

## 7. 人设选择

- 展示 6 类人设卡片（见 [cet-persona-design.md](cet-persona-design.md)）。
- 可根据 Profile 推荐，允许家长覆盖。

---

## 8. 与后台边界

- 无 Admin API 调用。
- Prompt/LLM/MCP 配置不在用户前台暴露。

---

## 9. 感知与无障碍

- 大点击区；支持键盘；动画克制（降低眩晕）。
- 实现期指定前端模型：见 [agents.md](../agents.md) §5。

---

## 10. MVP

| 能力 | MVP | 状态 |
|---|---|---|
| 登录 + 注册 + 个人中心 + CET 文本陪练页 | 1 / 3c / register / settings | ✓（`/login` `/register` `/home` `/settings` `/cet` `/cet/session/[id]` `/cet/report/[id]`） |
| 课时历史列表 + 回看 + 断点续课 | history | ✓（`/cet` 列表、`/cet/history/[id]`、session hydration；儿童原音不落库） |
| 上课记录单条/批量硬删除 | history-delete | ✓（行内删除 +「管理」勾选批量；级联清子表） |
| 通用 `/chat` 最小页 | 3c | ✓ |
| 语音按钮与播放 | 2B2 | ✓（16k WAV 录音 → `audioBase64`；消费 `audio.tts`；儿童气泡：ASR 文案 + 本地录音回放（`asr.transcript`）；CET API 经 Route Handler 代理，避免 Next rewrite 1MB 上限导致 `SSE HTTP 500`） |
| 开场外教先说 + 语音优先 UI | 小循环 UX | ✓（`opening=true`；儿童主界面不展示发音分） |
| 通话壳在场感 + 情绪贴纸 | presence A+C | ✓（PersonaStage 五态；字幕/折叠历史；前端启发式贴纸） |
| 真人肖像 + speaking 视频 + 课题氛围 | presence polish C | ✓（静帧多表情；speaking.webm；topic→theme） |
| 一轮分段耗时埋点 | timing | ✓（`timing_json` + SSE `turn.timing` + 静默 client-timing） |
| 家长报告页产品化 | 4 | ✓ 首版：`/cet/report/[id]` 展示课次元数据、`parentSummary`、参考分、错例、建议重点；HITL 未做 |

**鉴权：** JWT 存 `localStorage`（`kidora_token`），请求 `Authorization: Bearer`。`apiJson` 遇 401（含空 body）会清除 token，并提示重新登录。  
**代理：** `/api/auth|chat|learners` → `:8080`；`/api/cet` → `:8082`。  
**语音：** 进入会话页先 `opening` SSE；主交互为「录音」；可选展开打字；通话壳以人设头像与听/说状态为主，字幕区展示当前句（语音轮先「（语音）」再替换 ASR）；历史可折叠且全文可读；优先自动播放 MCP `audio.tts`（朗读稿经 `speakableForTts` 去掉括号中文注释）；字幕/历史常驻「播放」重听（外教 TTS / 孩子本机录音）；自动播被拦时「点这里听」；发音分与耗时数字不对儿童主界面展示；开播后静默上报 `e2eHeardMs`。真实外教音色需 mcp-server 配讯飞/腾讯（非 stub）。