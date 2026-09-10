# 通用 Agent 执行流程

描述 `kidora-agent-server`（及被 `cet-tutor-core` 复用的）通用 Agent 编排、SSE、审计与失败策略。  
CET 专用大/小循环见 [cet-tutor-design.md](cet-tutor-design.md)。

---

## 1. 设计原则

1. 优先 Spring AI Alibaba Agent Framework（如 ReactAgent / Workflow），禁止自研主循环。
2. **必须**设置 `max-model-calls` / `max-tool-rounds`；达上限返回明确错误码。
3. 阻塞调用离开 event-loop；SSE 与业务线程隔离。
4. 每次 LLM 调用写入 `llm_call_log`。

---

## 2. 包与核心类（阶段 3c）

| 组件 | 职责 |
|---|---|
| `ModelRouter` | primary + fallbacks（CHAT）；包装审计的 `callText` / `streamText` |
| `LlmClientFactory` | 按 `llm_config` 缓存 OpenAI Compatible `ChatClient` |
| `PromptTemplateService` | 读 `prompt_template` + `{{var}}` 渲染 |
| `LlmCallAuditor` | 写 `llm_call_log`（含 `learner_id` / `biz_source`） |
| `DetachedBlockingMono` | 阻塞 JDBC/LLM 离开 WebFlux 取消路径 |
| `ChatFacade` | 通用 Chat：`chat_session`/`chat_message` 短窗 + 流式回复（**无工具**） |
| `AgentFactory` | 空壳；`kidora.agent.max-model-calls` 占位，ReactAgent/MCP 后续 |
| MCP Client | **未实现** |

根包：`com.wuji.kidora.ai.agent`（core）、`com.wuji.kidora.ai.agent.server`（Boot）、`com.wuji.kidora.ai.cet.core` / `cet.server`（CET）。

CET 主路径使用 **ChatClient 结构化调用 + 课时状态机**；Tutor 输出 **先完整生成 → L2 闸门 → 分块 SSE**。通用 Chat 同用 `ModelRouter.streamText`，`biz_source=CHAT`。

---

## 3. 主执行流程（通用 Chat）

```
Request (User JWT)
  → 鉴权
  → ChatFacade：写 user 消息 → 近 N=20 条短窗
  → PromptTemplate（chat.system / chat.user）
  → ModelRouter.streamText（审计）
  → SSE：message.delta* / error / done
  → 写 assistant 消息
```

本期不做 Memory 异步抽取、不做工具轮次。达上限错误码 `AGENT_MAX_ITERATIONS` 预留给后续 Agent 环。
---

## 4. Checkpoint 与 Chat Memory

- 短记忆：当前 session 窗口 + watermark，禁止无限历史入模。
- Checkpoint：用于中断恢复（实现期按框架能力开启）。
- CET 会话状态以 `cet_lesson_session` / `cet_training_plan` 为准，不与通用 `chat_session` 混用主键语义。

---

## 5. 工具装配

1. 内置 Tool（平台）与 MCP Tool（远程）统一注册名；冲突 fail-fast。
2. `kidora.mcp.enabled=true` 时经 Client 拉取 ACTIVE 绑定工具。
3. CET 进程可挂载语音类工具子集；通用 Chat 默认不挂载发音评测（可配置）。

详见 [mcp-design.md](mcp-design.md)。

---

## 6. SSE 约定（目标）

| 事件 | 含义 |
|---|---|
| `message.delta` | 文本增量 |
| `tool.start` / `tool.end` | 工具调用 |
| `safety.block` | 安全拦截 |
| `cet.plan` | CET：计划就绪（仅 CET 流） |
| `cet.assessment` | CET：阶段评测 |
| `error` | 错误码 + 可展示文案 |
| `done` | 结束 |

须支持断线后续传策略（实现期：`Last-Event-ID` 或 session cursor）。

---

## 7. 入模审计

`llm_call_log` 至少包含：`config_id`、model、prompt 摘要或全文（按安全策略）、token、latency、`session_id`、`trace_id`、调用方（`CHAT` \| `CET_PLAN` \| `CET_TUTOR` \| `CET_EVAL` \| `SAFETY`）。

---

## 8. 主备模型

- `ModelRouter`：primary 失败可切换 fallbacks（同 kind=`CHAT`）。
- Embedding 独立 `config_id`，禁止与对话行混用 `model` 字段。

---

## 9. 失败策略

| 场景 | 策略 |
|---|---|
| 达最大轮次 | 返回 `AGENT_MAX_ITERATIONS`，不静默截断无提示 |
| MCP 超时 | 工具级失败回传 Agent；可熔断（分期） |
| Safety 拒绝 | 终止当轮，写 `cet_safety_event`，儿童侧友好提示 |
| LLM 5xx | failover → 最终错误事件 |

---

## 10. 与 Memory / RAG / MCP / CET 边界

| 能力 | 通用 Chat | CET |
|---|---|---|
| Memory | `kidora-memory` | 同库；读写 `learner_*` |
| RAG | 可选 Tool | MVP-5 课程检索 |
| MCP | 可选 | MVP-2 语音工具为主 |
| Planner | 无（或轻量） | `cet-tutor-core` 大循环 |
