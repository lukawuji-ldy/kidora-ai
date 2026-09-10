# CET 课时流程（Lesson Flow）

从开课到结课的状态、输入输出与 SSE 节奏。总设计见 [cet-tutor-design.md](cet-tutor-design.md)。

---

## 1. 参与者输入

| 输入 | 来源 |
|---|---|
| `learnerId` | 家长选择的儿童档案 |
| `topic` | 用户选定或推荐 |
| `personaId` | 用户选或 Profile 推荐 |
| `cefr` / 年龄段 | Profile；可覆盖 |
| 用户轮次内容 | 文本（MVP-1）或音频（MVP-2） |

---

## 2. 状态机

```
CREATED
  → PLANNING          # Planner 生成计划
  → PRACTICING        # Tutor 小循环
  → EVALUATING        # 阶段/会话评测
  → REPLANNING        # 可选，回到 PRACTICING
  → COMPLETED
  → ABORTED           # 用户退出
  → SAFETY_BLOCKED    # 安全终止
```

表字段见 [database-design.md](database-design.md) §3.1。

---

## 3. 阶段模板（可配置）

默认 stage 示例：

1. `warmup` — 问候与主题热身  
2. `vocab` — 3～5 个关键词  
3. `model` — 外教示范  
4. `dialog` — 目标轮次对话（螺旋难度）  
5. `wrapup` — 鼓励总结  

Planner 可按 CEFR / Profile 增删 stage；Re-Planner 可插入 `micro_drill`（如 have/has）。

---

## 4. 开课序列（MVP-1）

鉴权：`Authorization: Bearer <家长JWT>`；body 含 `learnerId`（须归属当前 user）。

```
POST /api/cet/sessions  {learnerId, topic, personaId?}
  → 归属校验 learner_profile
  → Safety(topic)
  → 读 Learner Profile
  → PLANNING → Planner → cet_training_plan
  → 状态 PRACTICING
  → 同步返回 {sessionId, status, planSummary}
```

**说明：** MVP-1 开课为同步 JSON，不在开课路径推 SSE `cet.plan`；Tutor 首轮由客户端再调 `/stream`。

---

## 5. 陪练轮次序列（小循环）

```
POST /api/cet/sessions/{id}/stream
  body: { text? | audioBase64?, locale?, referenceText? }
  → （有 audio）MCP asr_transcribe → childText
  → Safety(user utterance)
  → 写入 cet_tutor_turn
  → Tutor 生成反馈+下一问（受当前 plan 约束；禁止 Planner）
  → （kidora.mcp.enabled）MCP tts_synthesize
  → （有 audio + referenceText）MCP pronunciation_score
  → SSE：message.delta* → [audio.tts?] → [pronunciation?] → done
     （硬拦：safety.block + error）
```

**说明：** MVP-2B1 已落地 API/SSE 契约；Web 麦克风/播放属 **MVP-2B2**。无 MCP Bean 时纯文本路径与 MVP-1 一致（仅 `message.delta`）。

**禁止**在此路径调用完整 Planner/Re-Planner。

结课：`POST /api/cet/sessions/{id}/complete` → EVALUATING → 会话 assessment + `cet_session_report` → COMPLETED。

---

## 6. 阶段评测与 Re-plan

触发条件（配置化）：

- 完成 `targetTurns` 或 stage 目标
- 连续 N 轮同类错误超阈
- 用户/家长请求「太难/太简单」（后期）

```
EVALUATING
  → Evaluator 结构化结果
  → 若达标且无剩余 stage → wrapup → COMPLETED → 报告 + Memory
  → 若未达标 → REPLANNING → 新 plan revision → PRACTICING
```

---

## 7. 结课

- 写 `cet_session_report`（MVP-1 儿童摘要；MVP-4 家长版字段）。
- 结课同步更新 Learner Profile `extra_json` + `learner_semantic_memory`（**MVP-3 已落地**）。
- SSE `done`。

---

## 8. 异常路径

| 情况 | 行为 |
|---|---|
| Safety 命中 | `SAFETY_BLOCKED` 或单轮拒绝并继续（按策略） |
| LLM 失败 | failover；仍失败则友好错误，会话可 ABORT |
| 超时无响应 | 前端可重连 SSE；服务端以 turn cursor 续传 |

---

## 9. MVP

| 流程切片 | MVP |
|---|---|
| 开课 + 文本小循环 + 简单结课摘要 | 1 |
| 语音进出 | 2 |
| RePLANNING + revision 表 | 3（已落地） |
| 家长报告 HITL 检查点 | 4 |
