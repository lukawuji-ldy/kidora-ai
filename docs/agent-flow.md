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
| `ModelRouter` | primary + fallbacks（CHAT）；可选 `kidora.model.caller-config-ids` 按 `bizCaller` 覆盖（如 CET 陪练 `CET_TUTOR → llm_chat_primary`）；包装审计的 `callText` / `streamText`（CET 等仍用） |
| `LlmClientFactory` | 按 `llm_config` 缓存 OpenAI Compatible `ChatModel` / `ChatClient` |
| `PromptTemplateService` | 读 `prompt_template` + `{{var}}` 渲染 |
| `LlmCallAuditor` | 写 `llm_call_log`（含 `learner_id` / `biz_source`） |
| `DetachedBlockingMono` | 阻塞 JDBC/LLM 离开 WebFlux 取消路径 |
| `ChatFacade` | 通用 Chat：`chat_session`/`chat_message` 短窗 + **有界 ReactAgent** 流式（**无工具**） |
| `AgentFactory` | 按 `configId` 缓存 ReactAgent；`PostgresSaver`/`MemorySaver` + `max-model-calls` / `max-tool-rounds` |
| `CheckpointSaverFactory` | 构建进程单例 Checkpoint Saver（默认 Postgres，DDL 由 Flyway 管理） |
| MCP Client | **未实现**（本期 Chat 不挂工具） |

根包：`com.wuji.kidora.ai.agent`（core）、`com.wuji.kidora.ai.agent.server`（Boot）、`com.wuji.kidora.ai.cet.core` / `cet.server`（CET）。

CET 主路径使用 **ChatClient 结构化调用 + 课时状态机**；Tutor 输出 **先完整生成 → L2 闸门 → 分块 SSE**。通用 Chat 走 ReactAgent + Checkpoint，`biz_source=CHAT`。

---

## 3. 主执行流程（通用 Chat）

```
Request (User JWT)
  → 鉴权
  → ChatFacade：写 user 消息 → 近 N=20 条短窗（user/assistant Message 列表）
  → PromptTemplate（chat.system → RunnableConfig metadata）
  → AgentFactory.getOrCreate → ReactAgent.streamMessages（threadId=userId:sessionId）
  → SSE：message.delta* / error / done
  → 写 assistant 消息 + llm_call_log 审计
```

本期不做 Memory 异步抽取、不做工具轮次。达上限错误码 `AGENT_MAX_ITERATIONS`。

---

## 4. Checkpoint 与 Chat Memory

- **Chat Memory（用户可见）**：`chat_session` / `chat_message` 短窗；禁止把 checkpoint 当聊天历史展示。
- **Graph Checkpoint（运维可回放）**：Spring AI Alibaba `PostgresSaver` → 表 `GraphThread` / `GraphCheckpoint`（库内小写 `graphthread` / `graphcheckpoint`）。
  - `thread_name` = 业务 `threadId` = `userId:sessionId`
  - 配置：`kidora.agent.checkpoint.*`（默认 `type=postgres`，`create-tables=false`）
  - 管理台只读回放：`GET /api/admin/logs/checkpoints/**`（旁路 `kidora-ai-manage`）
- CET 会话状态以 `cet_lesson_session` / `cet_training_plan` 为准，**不**写入 Graph Checkpoint。

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
- **按调用方覆盖：** `kidora.model.caller-config-ids`（map：`bizCaller` → `llm_config.config_id`）。命中则优先打开该配置，不可用再回退 primary 链。CET 陪练默认示例：`CET_TUTOR: llm_chat_primary`（更快 chat 模型）；`SAFETY` / `CET_PLAN` / `CET_EVAL` 未映射时仍走 `primary-config-id`。
- Embedding 独立 `config_id`，禁止与对话行混用 `model` 字段。
- 管理台改 `llm_config` 后须**重启** agent / cet 进程方可加载新连接参数。

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
