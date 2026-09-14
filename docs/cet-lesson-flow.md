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
  → 同步返回 {sessionId, status, planSummary, childGoals?, vocabHints?, propAssets?}
```

**说明：** 开课为同步 JSON，不在开课路径推 SSE；进入陪练页后客户端先 `GET /sessions/{id}` + `GET .../turns`：无 turn 且 PRACTICING 才调 `/stream`（`opening=true`）拉外教开场；已有 turn 则 hydration 后直接陪练（断点续课）。`childGoals`（≤3 条儿童递进目标）与 `vocabHints`（≤5）由活跃 `plan_json` 解析；`propAssets` 为本地教具库命中预取，**只用于预热图片缓存，不决定舞台布局**（舞台由每轮 SSE `turn.prop` 驱动）；开课与续课/刷新共用 `propCandidateHints`，结果必然一致。详见 [2026-09-11-cet-lesson-goals-props-autolisten-design.md](superpowers/specs/2026-09-11-cet-lesson-goals-props-autolisten-design.md)、[2026-09-14-cet-prop-realtime-stage-design.md](superpowers/specs/2026-09-14-cet-prop-realtime-stage-design.md)。

---

计划生成后会异步幂等创建缺失道具任务，任务写入失败不影响开课；管理台生成并审核通过后，后续会话才使用新资产。

## 4.1 历史回看与续课

| API | 用途 |
|---|---|
| `GET /api/cet/sessions?learnerId=` | 课时列表 |
| `GET /api/cet/sessions/{id}` | 元数据 / planSummary / childGoals / vocabHints / propAssets / hasReport |
| `GET /api/cet/props/{lemma}` | 教具只读取图（JWT；本地库 ACTIVE） |
| `GET /api/cet/sessions/{id}/turns` | 文本 transcript |
| `POST /api/cet/sessions/{id}/abort` | 中止 → ABORTED |
| `DELETE /api/cet/sessions/{id}` | 硬删除单条（级联子表；不删 `llm_call_log`） |
| `POST /api/cet/sessions/batch-delete` | 硬删除批量（body: `{sessionIds}`，去重上限 50，事务整批） |
| `POST /api/cet/sessions/{id}/turns/{turnIndex}/tts` | 外教按需重 TTS（不落库） |

Web：`/cet` 列表（行内删除 +「管理」批量勾选）；`/cet/history/[id]` 只读回看；儿童历史录音不落库（见规格 `2026-09-10-cet-lesson-history-resume-design.md`）。删除为物理删除、不可恢复；「练习中」单条删除与任意批量删除需前端确认。级联表：`cet_tutor_turn` / `cet_turn_assessment` / `cet_session_report` / `cet_training_plan` / `cet_training_plan_revision` / `cet_safety_event` / `cet_lesson_session`。详见 `docs/superpowers/specs/2026-09-10-cet-lesson-history-delete-design.md`。

---

## 5. 陪练轮次序列（小循环）

### 5.1 开场（外教先说）

```
POST /api/cet/sessions/{id}/stream
  body: { opening: true }
  → 校验 PRACTICING 且尚无 tutor turn（否则 CET_INVALID_STATE）
  → Tutor 开场（打招呼 + 点题 + 一个问题；prompt cet.tutor.opening.*）
  → Safety(output) → 写入 cet_tutor_turn（child_text=null, stage=warmup）
  → （kidora.mcp.enabled）MCP tts_synthesize
  → SSE：message.delta* → turn.prop → [audio.tts?] → turn.timing → done
```

**禁止**在开场路径调用 Planner/Re-Planner。

### 5.2 孩子一轮

```
POST /api/cet/sessions/{id}/stream
  body: { text? | audioBase64?, locale?, referenceText? }
  → （有 audio）MCP asr_transcribe → childText
  → Safety(user utterance)（HARD/SOFT L0；课堂短答可 `L0_FAST_ALLOW` 跳过入站模型）
  → 写入 cet_tutor_turn
  → Tutor 生成反馈+下一问（受当前 plan 约束；禁止 Planner；隐式纠错优先，必要时中文讲解+英文示范）
  → Safety(output)（短鼓励脚手架可 `L0_OUT_SKIP_MODEL` 跳过出站模型；否则仍打模型；fail-closed）
  → （kidora.mcp.enabled）MCP tts_synthesize → 立即发 audio.tts（ttsReadyMs 在此打点）
  → （有 audio + referenceText）MCP pronunciation_score（**不**挡听感；TTS 下发后再跑）
  → （达门槛）阶段评测 / plan.updated?
  → SSE：[asr.transcript?] → message.delta* → turn.prop → [audio.tts?] → [pronunciation?] → [plan.updated?] → turn.timing → done
     （有 audio 且 ASR 成功时必有 asr.transcript，排在 message.delta 前；
      soft redirect：asr.transcript? → message.delta → turn.timing（无 turn.prop）；
      硬拦：asr.transcript? → safety.block + error；
      turn.prop：本轮教具舞台 JSON `{layout,activeLemma,assets[]}`，由 PropStageDirector
      优先采信外教输出末尾的 `[[PROP:词]]` 声明（该标记在闸门/落库/SSE/TTS 之前已剥离），
      声明缺失或不可用时退回按字幕点名顺序；两者都没有时发 personaFocus；判定异常只 WARN
      并退化为 personaFocus，不中断主流。前端只渲染，不再从字幕推断；
      turn.timing：服务端分段耗时 JSON，落库 cet_tutor_turn.timing_json；儿童 UI 不展示；
      **ttsReadyMs 不含 scoreMs**；pronunciation 仍下发但不阻塞外教开播）
```

**客户端 e2e 上报（静默）：**

```
POST /api/cet/sessions/{id}/turns/{turnIndex}/client-timing
  body: { e2eHeardMs: number }   // 0..600000；停麦→外教开播
  → 合并写入 timing_json.client；幂等覆盖；无行 404
```

**儿童侧交互：** 通话壳（真人感人设头像 + 状态机 + 课题氛围装饰）+ CaptionStrip 字幕；历史可折叠；情绪贴纸与主题包均为前端装饰，无后端 emotion/theme 字段。见 [ui-design.md](ui-design.md)、[2026-09-11-cet-persona-presence-polish-design.md](superpowers/specs/2026-09-11-cet-persona-presence-polish-design.md)。

**说明：** MVP-2B1 已落地 API/SSE 契约；Web 麦克风/播放属 **MVP-2B2**（并已对齐开场 + 语音优先 + 通话壳在场感）。无 MCP Bean 时纯文本路径仍发 `turn.timing`。`pronunciation` 仍下发，儿童主界面默认不展示分数。听感关键路径：发音评测已移出（O3 / `speechExtrasFlux`）；Safety 课堂短答/短鼓励可跳过模型（O1）。详见 [gate-analysis](superpowers/specs/2026-09-11-cet-turn-latency-gate-analysis.md)、[O1](superpowers/specs/2026-09-11-cet-turn-latency-o1-safety-design.md)。

**禁止**在此路径调用完整 Planner/Re-Planner。

结课：`POST /api/cet/sessions/{id}/complete` → EVALUATING → 会话 assessment + `cet_session_report` → COMPLETED`（手动「结束并总结」）。阶段评测 `decision=complete` 时进入告别子流程（`extra_json.wrapUpPhase`）：`session.wrapup` → 外教第 1 句（`wrapUp:true`）→ 孩子回 → 外教第 2 句 + `session.completed` 自动结课。告别两句都会发 `turn.prop`（`personaFocus`）把舞台收回人像。详见 [2026-09-14-cet-auto-complete-farewell-design.md](superpowers/specs/2026-09-14-cet-auto-complete-farewell-design.md)。

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
