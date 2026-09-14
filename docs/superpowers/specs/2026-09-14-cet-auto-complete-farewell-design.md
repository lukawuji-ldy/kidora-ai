# CET 自动结课与告别流程设计

- **日期：** 2026-09-14
- **仓库：** `kidora-ai`（`cet-tutor-core` / `cet-tutor-server` / `kidora-web` / `kidora-agent-server` Flyway）
- **状态：** Implemented
- **关联：** [docs/cet-lesson-flow.md](../../cet-lesson-flow.md)、[docs/ui-design.md](../../ui-design.md)、[docs/mcp-design.md](../../mcp-design.md)、[2026-09-10-cet-opening-en-greeting-design.md](./2026-09-10-cet-opening-en-greeting-design.md)

## 1. 背景与目标

阶段评测已可返回 `decision=complete`，但当前仅 SSE `plan.updated` 提示，会话仍停留在 `PRACTICING`，需用户手动「结束练习」才结课。家长需要区分 **暂停（可续课）** 与 **结束并总结（立刻结课、不说再见）**；达标自动结课前需完成 **外教再见 → 孩子回再见 → 外教再鼓励** 三轮互动。

**目标：**

1. Evaluator `complete` 后进入告别子流程，告别结束后服务端自动结课并下发 `session.completed`。
2. 顶栏 **暂停** / **结束并总结**；手动结课跳过告别。
3. 暂停不改变 `PRACTICING`，支持断点续课（含告别中途）。

**非目标：**

- 新增 `LessonStatus.WRAPPING` 枚举（用 `extra_json.wrapUpPhase`）。
- 家长 HITL、报告家长版字段。
- 服务端定时器进程（45s 兜底由前端触发专用 stream 请求）。

## 2. 已确认决策

| 项 | 选择 |
|---|---|
| 告别轮次 | B：外教再见 → 孩子回 → 外教再鼓励 → 结课 |
| 手动「结束并总结」 | 立刻 `POST .../complete`，不说再见 |
| 手动「暂停」 | 不调 API，离开页面，保持 `PRACTICING` |
| 告别中暂停 | 允许；续课时按 `wrapUpPhase` 继续 |
| 告别中「结束并总结」 | 允许；跳过剩余告别 |
| 孩子 45s 无有效输入 | 前端触发 `wrapUpTimeout`，服务端代发第 3 句并结课 |
| 实现策略 | `extra_json.wrapUpPhase` + `cet.tutor.wrapup.*` Prompt（方案 1） |

## 3. `wrapUpPhase`（`cet_lesson_session.extra_json`）

与 `lastStageEvalTurn` 等同库合并读写，禁止整对象覆盖丢字段。

| 值 | 含义 |
|---|---|
| （缺省） | 正常陪练 |
| `pending_tutor_farewell` | 阶段评测 `complete`；尚未播外教第 1 句再见 |
| `await_child_farewell` | 外教第 1 句已写入 turn；等待孩子回再见 |
| `pending_final_tutor` | 孩子已回；待生成外教第 2 句（鼓励）并结课（同轮 SSE 内完成，一般不停留） |

附加字段：

- `pendingChildSummary`：阶段评测摘要，供最终 `complete()` 写报告时优先使用（若 empty 则走会话 eval）。

清除：结课 / 中止 / 安全终止时删除 `wrapUpPhase` 与 `pendingChildSummary`。

## 4. 服务端流程

### 4.1 触发自动告别

在 `maybeStageEvaluate` 中，当 `eval.decision == COMPLETE`：

1. 不再仅发 `plan.updated` 后回到普通陪练。
2. 写入 `wrapUpPhase=pending_tutor_farewell` + `pendingChildSummary`。
3. 当前孩子轮 SSE **尾部**追加：`generateWrapUpTutor( step=1 )` → 落库 turn（`stageId=wrapup`）→ Safety/TTS → `session.wrapup` JSON → `turn.timing`。
4. 更新 `wrapUpPhase=await_child_farewell`。

阶段评测期间仍短暂 `EVALUATING` → `PRACTICING`；告别期间保持 `PRACTICING`。

### 4.2 孩子回再见（`await_child_farewell`）

下一次 `streamTurn`（text/voice）：

1. 走输入 Safety；写入 child turn。
2. `generateWrapUpTutor( step=2, childText )` → 落库 → TTS。
3. 调用现有结课逻辑（`completeInternal`：assessment、report、memory、`COMPLETED`），`childSummary` 优先 `pendingChildSummary`。
4. SSE 发 `session.completed`（含 `childSummary`、`sessionId`）→ `turn.timing` → `done`。
5. 清除 wrap-up 字段。

`streamTurn` 在 `await_child_farewell` 时 **禁止** 调用 `generateReply` / `maybeStageEvaluate`。

### 4.3 仅外教告别（断点 / 评测后未播完）

`POST .../stream` body：`{ "wrapUp": true }`（与 `opening` 互斥）

- 条件：`PRACTICING` 且 `wrapUpPhase=pending_tutor_farewell`。
- 行为：同 §4.1 第 3 步（无儿童输入）。

### 4.4 45s 超时兜底

`POST .../stream` body：`{ "wrapUpTimeout": true }`

- 条件：`await_child_farewell`。
- 行为：不写入 child turn；直接 `generateWrapUpTutor(step=2, childText="")` → 结课 → `session.completed`。

### 4.5 手动结课

`POST .../complete` 不变：任意 `PRACTICING`（含告别中）可立刻结课；清除 wrap-up 字段。

## 5. Tutor / Prompt

新增模板（Flyway **V28**，若占用则顺延）：

- `cet.tutor.wrapup.system` / `cet.tutor.wrapup.user`

变量：`wrapUpStep`（1|2）、`displayName`、`personaId`、`topic`、`planSummary`、`childFarewell`（step2）、`recentTurns`。

约束（中文正文，协议键英文）：

- Step 1：英主再见 + 一句短鼓励；**不得**出新练习问句。
- Step 2：回应孩子告别 + 再鼓励；**不得**出新问句；语气收束。

`TutorLoop.generateWrapUpReply(...)` 加载上述模板。

可选：`cet.eval.system` 增补一句——达标时 `decision=complete` 表示进入 wrap-up，非立即结课。

## 6. SSE

| event | data（JSON 或文本） |
|---|---|
| `session.wrapup` | `{ "phase": "await_child_farewell", "step": 1 }` |
| `session.completed` | `{ "sessionId", "status":"COMPLETED", "childSummary" }` |

`CetStreamEvent` 新增 `WRAPUP`、`SESSION_COMPLETED`；`CetSessionController.toSse` 映射上述 event 名。

顺序（告别第 2 轮）：`[asr?] → message.delta* → [audio.tts?] → session.completed → turn.timing → done`。

## 7. API 扩展

### 7.1 `GET /api/cet/sessions/{id}`

响应增加：`wrapUpPhase`（string | null）。

### 7.2 `POST /api/cet/sessions/{id}/stream`

请求体扩展（互斥校验）：

- `opening?: boolean`
- `wrapUp?: boolean`
- `wrapUpTimeout?: boolean`
- 否则为普通陪练 `{ text?, audioBase64?, ... }`

## 8. Web（`kidora-web`）

### 8.1 顶栏

| 控件 | 行为 |
|---|---|
| **暂停** | 停麦/TTS/自动听调度 → `router.push('/home')`；无 API |
| **结束并总结** | 现有 `complete()` → `/cet/report/[id]` |

移除单一「结束练习」文案。

### 8.2 告别模式 UI

- `wrapUpPhase === await_child_farewell`：副文案「跟老师说再见吧」；仍可用语音/打字；**不**推进 lesson goals；自动听可在 TTS 结束后开启。
- `pending_tutor_farewell`：hydration 后自动 `POST stream { wrapUp: true }`（thinking → speaking）。
- 监听 `session.wrapup` / `session.completed`（扩展 `postSse` handlers）。
- `session.completed` + 外教 TTS `onEnded` → `router.push` 报告页（若 TTS 无音频则 `onDone` 即跳）。
- 45s 无孩子轮次 → `wrapUpTimeout` stream。

### 8.3 报告页

保持「回首页」「再练一次」（已实现）。

## 9. 测试

| 范围 | 用例 |
|---|---|
| `CetLessonService` | complete 决策 → phase 迁移；wrapUp step1/2；timeout；手动 complete 清 phase |
| `TutorLoop` | wrapup prompt vars |
| `CetSessionController` | SSE event 名 |
| Web | 可选轻量单测；主路径手测清单见 plan |

## 10. 文档同步

- `docs/cet-lesson-flow.md` §5–§7
- `docs/ui-design.md` §5 顶栏与告别
- `docs/mcp-design.md` SSE 表
- `agents.md` 若对外 API 摘要需一行

## 11. 验收

1. 练到达 `complete` 后外教先说再见，孩子回，外教再鼓励，自动进小结页。
2. 「暂停」后 status 仍为 PRACTICING，可续课；告别中途暂停可续。
3. 「结束并总结」任意时刻立刻小结，无额外告别轮。
4. 45s 不说话仍能结课进小结。
5. `mvn test`（`cet-tutor-core`）通过。
