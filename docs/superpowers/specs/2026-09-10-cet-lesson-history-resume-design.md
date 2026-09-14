# CET 课时历史保存与还原设计

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`（`cet-tutor-core` / `cet-tutor-server` / `kidora-web`）
- **状态：** Accepted
- **关联：** [docs/cet-lesson-flow.md](../../cet-lesson-flow.md)、[docs/ui-design.md](../../ui-design.md)、[docs/architecture.md](../../architecture.md)

## 1. 背景与目标

CET 已在开课/陪练/结课路径写入 `cet_lesson_session`、`cet_tutor_turn`（文本）、计划与儿童报告，但缺少列表/transcript 读取 API 与 Web 历史页；会话页刷新后无法可靠 hydration；音频仅 SSE/本地内存，不落库。

**目标（分期）：**

1. **Phase A — 只读回看：** 按 learner 列课时；打开某次课看文本 transcript；COMPLETED 可进报告。
2. **Phase B — 断点续课：** 重进 `PRACTICING` 会话 hydration 后继续练；已有 turn 禁止重复 `opening`。
3. **Phase C1 — 外教历史听：** 回看页按 `tutor_text` 按需重调 MCP TTS（不存 TTS 副本）。
4. **Phase C2 — 儿童原音归档：** **默认不做**（见 §1.2）；需产品/合规另开规格。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 落库 | 不新增会话快照表；复用现有 turn/session |
| 列表归属 | JWT `userId` + `learnerId` 归属校验；禁止信任前端 `userId` |
| 续课 | 读库状态与 turns；禁止重跑 Planner |
| 外教音频 | C1：按需重合成 |
| 儿童音频 | C2：延后；维持「不落库」直至独立规格批准 |

### 1.2 非目标（含 C2 闸门）

- **儿童录音对象存储 / URL / 会话重进后回放历史原音**（C2）：仅当产品明确要求时另开规格（存储厂商、TTL 默认 90 天、签名 URL、谁可听、硬删任务）。在此之前保持与 [cet-child-asr-playback](2026-09-10-cet-child-asr-playback-design.md) 一致的非目标。
- 把历史整段对话喂回 Tutor 上下文（仍短记忆窗口）
- 家长 HITL / `parent_summary` 产品化（MVP-4）
- 管理台课时运营页
- SSE turn-cursor 断线续传完整协议
- 将 TTS/儿童音频 base64 写入 PostgreSQL

## 2. API

| 方法 / 路径 | 说明 |
|---|---|
| `GET /api/cet/sessions?learnerId=` | 该学习者课时列表（时间倒序） |
| `GET /api/cet/sessions/{id}` | 元数据 + status + planSummary + hasReport |
| `GET /api/cet/sessions/{id}/turns` | 按 turnIndex 升序：tutor/child 文本 |
| `POST /api/cet/sessions/{id}/abort` | 可选中止 → `ABORTED` |
| `POST /api/cet/sessions/{id}/turns/{turnIndex}/tts` | C1：对该轮 `tutor_text` 重合成 TTS，返回 JSON |

归属：会话 `user_id` 必须等于 JWT；列表另校验 learner 归属。

### 2.1 列表项字段

`sessionId`, `learnerId`, `topic`, `personaId`, `cefrLevel`, `status`, `createTime`, `startTime`, `endTime`, `hasReport`

### 2.2 Turn 字段

`turnIndex`, `stageId`, `tutorText`, `childText`, `createTime`

### 2.3 TTS 响应

`audioBase64`, `mimeType`, `provider`（无 MCP / 合成失败 → 明确错误码，前端可 SpeechSynthesis 兜底）

## 3. 断点续课规则

1. 进入 `/cet/session/[id]`：先 `GET session` + `GET turns` hydration。
2. 若 turns 非空：**不**调用 `opening=true`。
3. 若 turns 为空且 status=`PRACTICING`：走现有开场流。
4. status 为 `EVALUATING` / `REPLANNING`：提示稍后或引导 complete/abort；不双开 stream。
5. status 为终态（`COMPLETED` / `ABORTED` / `SAFETY_BLOCKED`）：只读，跳转 history 或报告。

`assertOpeningAllowed`：已有 turn 时 `CET_INVALID_STATE`（服务端硬守卫）。

## 4. Web

| 路由 | 行为 |
|---|---|
| `/cet` | 开课表单 + 当前 learner 历史列表（继续 / 回看 / 报告） |
| `/cet/history/[id]` | 只读 transcript；外教气泡可 C1 重 TTS |
| `/cet/session/[id]` | 续课 hydration；终态重定向 history/report |

## 5. 验收

- **A：** 按 learner 列出历史；打开可见完整文本轮次；COMPLETED 可看儿童报告。
- **B：** 刷新未结课会话可见已聊内容并可继续；不重复开场。
- **C1：** 历史页外教可再听（允许合成延迟）。
- **C2：** 未实现；规格闸门见 §1.2。

## 6. 文档同步

同交付更新：`cet-lesson-flow.md`、`ui-design.md`、`architecture.md` API 表。
