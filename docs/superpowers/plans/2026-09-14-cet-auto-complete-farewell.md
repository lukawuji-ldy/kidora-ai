# CET 自动结课与告别 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 阶段评测 `complete` 后走三轮告别并自动结课；顶栏区分「暂停」与「结束并总结」。

**Architecture:** `cet_lesson_session.extra_json.wrapUpPhase` 驱动告别子状态；`TutorLoop.generateWrapUpReply` + Flyway `cet.tutor.wrapup.*`；SSE `session.wrapup` / `session.completed`；前端 `postSse` 扩展与 45s 超时。

**Tech Stack:** Java 17, Spring Boot 3.4.8, Next.js (`kidora-web`), PostgreSQL Flyway on `kidora-agent-server`.

## Global Constraints

- `groupId` `com.wuji.kidora.ai`；类型级 Javadoc 含 `@author liudy`。
- 无 parent POM；改 API/SSE 须同步 `docs/` 与 `agents.md`。
- 阻塞 JDBC/LLM 已在 server 隔离线程池；WebFlux SSE 不变。
- Prompt 正文中文；JSON 键名英文。
- 禁止信任前端 `userId`；JWT 归属校验不变。
- Flyway 下一序号 **V28**（实施前 ls `kidora-agent-server/src/main/resources/db/migration/V*.sql` 确认）。

---

## File map

| 文件 | 职责 |
|---|---|
| `cet-tutor-core/.../speech/CetStreamEvent.java` | 新事件类型 |
| `cet-tutor-core/.../tutor/TutorLoop.java` | `generateWrapUpReply` |
| `cet-tutor-core/.../service/CetLessonService.java` | wrap-up 编排、stream 分支、`SessionDetail.wrapUpPhase` |
| `cet-tutor-core/.../service/WrapUpPhase.java`（新建） | phase 常量 + extra_json 读写 |
| `cet-tutor-server/.../CetSessionController.java` | SSE 映射、StreamRequest 字段 |
| `kidora-agent-server/.../V29__cet_wrapup_prompt.sql` | Prompt |
| `kidora-web/src/lib/api.ts` | `onWrapUp` / `onSessionCompleted` |
| `kidora-web/src/app/cet/session/[id]/page.tsx` | 双按钮、告别模式、超时 |
| `docs/cet-lesson-flow.md`, `docs/ui-design.md`, `docs/mcp-design.md` | 文档 |

---

### Task 1: Stream 事件类型

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/speech/CetStreamEvent.java`
- Modify: `cet-tutor-server/src/main/java/com/wuji/kidora/ai/cet/server/web/CetSessionController.java` (`toSse`)
- Test: `cet-tutor-server/src/test/java/com/wuji/kidora/ai/cet/server/web/CetSessionControllerTest.java`

**Interfaces:**
- Produces: `CetStreamEvent.wrapUp(String json)`, `CetStreamEvent.sessionCompleted(String json)`；`Type.WRAPUP`, `Type.SESSION_COMPLETED`

- [ ] **Step 1:** 在 `Type` 枚举增加 `WRAPUP`, `SESSION_COMPLETED`；添加工厂方法。
- [ ] **Step 2:** `toSse` 映射 `session.wrapup` / `session.completed`。
- [ ] **Step 3:** 单测断言 `toSse(CetStreamEvent.wrapUp("{}")).event()` 等。
- [ ] **Step 4:** `cd cet-tutor-server && mvn -q test -Dtest=CetSessionControllerTest`

---

### Task 2: WrapUpPhase 工具类

**Files:**
- Create: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/WrapUpPhase.java`
- Test: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/WrapUpPhaseTest.java`

**Interfaces:**
- Consumes: `CetLessonSessionRepository.findExtraJson` / `updateExtraJson`
- Produces: `WrapUpPhase.read(repo, sessionId)`, `setPhase(...)`, `clear(...)`, `pendingSummary(...)`

常量：`PENDING_TUTOR_FAREWELL`, `AWAIT_CHILD_FAREWELL`；合并 `lastStageEvalTurn` 与 `pendingChildSummary`。

- [ ] **Step 1:** 写失败单测：merge 保留 `lastStageEvalTurn`，set/clear phase。
- [ ] **Step 2:** 实现 `WrapUpPhase` helper（`ObjectMapper` 注入或静态 parse）。
- [ ] **Step 3:** `cd cet-tutor-core && mvn -q test -Dtest=WrapUpPhaseTest`

---

### Task 3: TutorLoop 告别生成

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/tutor/TutorLoop.java`
- Test: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/tutor/TutorLoopTest.java`

**Interfaces:**
- Produces: `String generateWrapUpReply(int step, String planJson, String cefr, String personaId, String displayName, String topic, String childFarewell, List<TurnRow> recent, ModelRouter.CallContext ctx)`

- [ ] **Step 1:** 单测 mock `PromptTemplateService` 断言 step=1/2 加载 `cet.tutor.wrapup.system` 且 vars 含 `wrapUpStep`。
- [ ] **Step 2:** 实现方法（step 1 时 `childFarewell` 空串）。
- [ ] **Step 3:** `mvn -q test -Dtest=TutorLoopTest`

---

### Task 4: Flyway 告别 Prompt

**Files:**
- Create: `kidora-agent-server/src/main/resources/db/migration/V29__cet_wrapup_prompt.sql`

- [ ] **Step 1:** INSERT/UPDATE `prompt_template` + sync published `prompt_template_version`（参照 V13 模式）：
  - `cet.tutor.wrapup.system`：两步告别；禁止新练习问句；英主+括号中文；TTS 剥括号说明。
  - `cet.tutor.wrapup.user`：`wrapUpStep={{wrapUpStep}}`，step2 含 `childFarewell`。
- [ ] **Step 2:** 可选 `UPDATE cet.eval.system` 一句 complete→wrap-up 语义。

---

### Task 5: CetLessonService 告别与结课编排

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java`
- Test: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceWrapUpTest.java`（新建）

**Interfaces:**
- Consumes: Task 2 `WrapUpPhase`, Task 3 `generateWrapUpReply`, existing `complete` eval/report/memory
- Produces: `streamWrapUpTutor(...)`, `streamWrapUpAfterChild(...)`, `streamWrapUpTimeout(...)`, `maybeStageEvaluate` COMPLETE 分支改动

**要点：**

1. `maybeStageEvaluate`：`COMPLETE` → `WrapUpPhase.setPendingTutor` + 返回 events **不含**旧 `plan.updated complete`；由 `streamTurn` tail 调 `appendWrapUpStep1Flux`。
2. `streamTurn` 开头读 phase：
   - `await_child_farewell` → 走 wrap-up child 路径 + step2 + `completeAfterWrapUp` + `session.completed`。
   - 否则正常路径；tail 若刚设 `pending_tutor_farewell` 则 concat step1 flux。
3. 新增 `streamWrapUpOnly(userId, sessionId)` 供 `{wrapUp:true}`。
4. 新增 `streamWrapUpTimeout(userId, sessionId)`。
5. `complete(userId, sessionId)` 开头 `WrapUpPhase.clear`。
6. `getSession` → `SessionDetail` 增加 `String wrapUpPhase`（nullable）。
7. `completeAfterWrapUp`：assessment 仍跑；`childSummary` 优先 `pendingChildSummary`。

- [ ] **Step 1:** 单测：mock evaluator COMPLETE → extra_json phase + step1 flux 含 WRAPUP。
- [ ] **Step 2:** 单测：await_child 输入后 SESSION_COMPLETED + status COMPLETED。
- [ ] **Step 3:** 单测：manual `complete()` 清除 phase。
- [ ] **Step 4:** 实现（可抽 private `Flux<CetStreamEvent> emitWrapUpTutorTurn(...)` 减少重复）。
- [ ] **Step 5:** `cd cet-tutor-core && mvn -q test`

---

### Task 6: Controller Stream 请求体

**Files:**
- Modify: `cet-tutor-server/.../CetSessionController.java`（`stream` 路由分支）
- Modify: `StreamRequest` record 增加 `Boolean wrapUp`, `Boolean wrapUpTimeout`
- Modify: `getSession` JSON 增加 `wrapUpPhase`

- [ ] **Step 1:** `opening` / `wrapUp` / `wrapUpTimeout` 互斥校验，非法 → `CET_INVALID_STATE`。
- [ ] **Step 2:** 调用 `cetLessonService.streamWrapUpOnly` / `streamWrapUpTimeout`。
- [ ] **Step 3:** `mvn -q test`（server + core）

---

### Task 7: kidora-web 前端

**Files:**
- Modify: `kidora-web/src/lib/api.ts`
- Modify: `kidora-web/src/app/cet/session/[id]/page.tsx`

- [ ] **Step 1:** `SseHandlers` 增加 `onWrapUp?`, `onSessionCompleted?`；`postSse` 解析 `session.wrapup` / `session.completed`。
- [ ] **Step 2:** Session GET 类型增加 `wrapUpPhase?: string | null`。
- [ ] **Step 3:** 顶栏「暂停」「结束并总结」；暂停 `pauseLesson()` → stop voice/rec → `router.push('/home')`。
- [ ] **Step 4:** hydration：`pending_tutor_farewell` → `runWrapUpStream()`；`await_child_farewell` → 显示 hint + 45s timer ref。
- [ ] **Step 5:** `streamTurn` handlers：`onSessionCompleted` 存 summary；TTS 结束后 `router.push(/cet/report/...)`。
- [ ] **Step 6:** timer 触发 `postSse(..., { wrapUpTimeout: true })`。
- [ ] **Step 7:** 告别模式下禁用 stage eval 无关 UI（不增新题目标）。

---

### Task 8: 文档

**Files:**
- Modify: `docs/cet-lesson-flow.md`, `docs/ui-design.md`, `docs/mcp-design.md`
- Modify: `docs/superpowers/specs/2026-09-14-cet-auto-complete-farewell-design.md` 状态 **Implemented**（完成后）

- [ ] **Step 1:** 同步 SSE 表、顶栏、wrapUpPhase、自动结课路径。
- [ ] **Step 2:** `agents.md` 若有一节 CET API 摘要则补一行。

---

## Plan self-review

| Spec § | Task |
|---|---|
| wrapUpPhase | 2, 5 |
| 三轮告别 | 3, 4, 5 |
| SSE | 1, 5, 6, 7 |
| 暂停 / 结束并总结 | 7 |
| 45s | 5, 6, 7 |
| GET wrapUpPhase | 5, 6, 7 |
| Flyway V28 | 4 |
| 测试 | 1–5 |
| 文档 | 8 |

无 TBD；类型名与 spec 一致。

---

## Manual QA checklist

1. 短 plan / stub LLM 下模拟 complete（或降 `targetTurns`）→ 再见三轮 → 小结。
2. 暂停 → 首页 → 继续练 → 告别续上。
3. 告别中「结束并总结」→ 立刻小结。
4. 等待 45s → 自动小结。
