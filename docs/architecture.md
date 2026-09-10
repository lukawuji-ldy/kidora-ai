# 系统架构详细设计

本文档描述 Kidora 双仓库部署拓扑、模块依赖、端到端主链路与配置分区。  
表结构见 [database-design.md](database-design.md)；通用 Agent 流程见 [agent-flow.md](agent-flow.md)；CET 域见 [cet-tutor-design.md](cet-tutor-design.md)。

> **进度：** MVP-2B1 已落地：`kidora-mcp-server`（:8081，stub|azure）+ CET MCP Client（`kidora.mcp.enabled`）+ stream ASR/TTS/发音 SSE。Web 语音闭环属 MVP-2B2。

---

## 1. 部署拓扑

系统由 **本仓库前台 + 三个 Spring Boot 进程** 组成；Agent/CET 与 MCP 禁止合并为单进程强耦合。运营管理台为**第四类进程**，位于旁路 `kidora-ai-manage`。

```
        kidora-web (Next.js)                 kidora-admin-web (旁路)
                 |                                      |
                 |  HTTPS + User JWT / SSE              |  HTTPS + Admin JWT
                 |  /api/**（非 admin）                   |  /api/admin/**
                 v                                      v
    +---------------------------+         +---------------------------+
    | kidora-agent-server       |         | kidora-agent-manage       |
    | (:8080) 通用 Chat         |         | (旁路) 仅 Admin API       |
    +---------------------------+         +---------------------------+
                 |
    +---------------------------+
    | cet-tutor-server (:8082)  |  CET 陪练 / 报告
    +---------------------------+
                 |      |
         +-------+  +---+--------+
         v          v            v
      LLM API   PostgreSQL    MCP Client
                 |                 |
                 v                 v
              (可选 ES)     +------------------------+
                            | kidora-mcp-server      |
                            | Tools (:8081)          |
                            +------------------------+
```

| 进程 / 工程 | 端口（示例） | 对外暴露 |
|---|---|---|
| `kidora-web` | 3000 | 用户 UI（npm） |
| `kidora-agent-server` | 8080 | Auth、通用 Chat、SSE（无 Admin） |
| `kidora-mcp-server` | 8081 | 仅 MCP 协议端点 |
| `cet-tutor-server` | 8082 | CET：`/api/cet/**` |
| `kidora-admin-web` + `kidora-agent-manage` | 5173 / 8083 | **旁路**运营后台 |

**前后端联调（实现期）**

- 通用 Chat：Next.js `rewrites` → `kidora-agent-server`（`BACKEND_URL`，默认 `http://127.0.0.1:8080`）。
- CET：`/api/cet/*` → `cet-tutor-server`（`CET_BACKEND_URL`，默认 `http://127.0.0.1:8082`）。
- 管理：旁路 Vite proxy → `kidora-agent-manage`（默认 `:8083`）。

---

## 2. Maven 模块与依赖方向

**无 parent POM。** `groupId` = `com.wuji.kidora.ai`。通用 `kidora-*`，CET 域 `cet-*`。

```
kidora-agent-server
        │
        ▼
  kidora-agent-core
        │
   ┌────┼────┐
   ▼    ▼    ▼
kidora-memory kidora-rag kidora-common

cet-tutor-server ──► cet-tutor-core ──► kidora-agent-core / kidora-common
                                         │
                                         └──► kidora-memory（学习者画像）

kidora-mcp-server ──► kidora-common
```

**强制规则**

1. 禁止环依赖与反向依赖（库不得依赖 Boot 应用）。
2. `kidora-mcp-server` 不得依赖 `kidora-agent-core` / `cet-tutor-core`。
3. `cet-tutor-core` 可依赖 `kidora-agent-core`，但通用 Chat 路径不得依赖 `cet-*`。
4. 管理台仓库通过 Maven 依赖本仓库已 install 的 `kidora-*` jar，不复制源码。

| 模块 | 根包（约定） |
|---|---|
| `kidora-agent-server` | `com.wuji.kidora.ai.agent.server` |
| `kidora-mcp-server` | `com.wuji.kidora.ai.mcp` |
| `cet-tutor-server` | `com.wuji.kidora.ai.cet.server` |
| `kidora-agent-core` | `com.wuji.kidora.ai.agent` |
| `kidora-memory` | `com.wuji.kidora.ai.memory` |
| `kidora-rag` | `com.wuji.kidora.ai.rag` |
| `kidora-common` | `com.wuji.kidora.ai.common` |
| `cet-tutor-core` | `com.wuji.kidora.ai.cet.core` |

---

## 3. 端到端主链路

### 3.1 通用 Chat（已落地，无工具环）

```
用户 → kidora-web → kidora-agent-server
  → ChatFacade（chat_session / chat_message 短窗）
  → ModelRouter.streamText → SSE message.delta / done
```

本阶段**不**挂 ReactAgent / MCP；`AgentFactory` 为空壳，`kidora.agent.max-model-calls` 仅配置占位。

### 3.2 CET 陪练（文本已落地）

```
儿童/家长 → kidora-web（CET 页）→ cet-tutor-server
  → Safety Guard（L0 + L1 fail-closed；输出先检后发）
  → Lesson Planner → TrainingPlan
  → Tutor Loop（先完整生成 → L2 → 分块 SSE）
  → 结课 SessionEvaluator → childSummary
```

**关键：** Tutor 小循环内不调用完整 Planner；Re-plan / ASR/TTS 留待后续。

详情：[cet-lesson-flow.md](cet-lesson-flow.md)。

---

## 4. API 摘要（阶段 3c）

| 区域 | 方法 / 路径 | 宿主 | 说明 | MVP |
|---|---|---|---|---|
| Auth | `POST /api/auth/login` | agent-server `:8080` | 家长登录发 User JWT（claim：`userId`/`username`/`nickname`/`role`；**不含** learnerId） | 1 ✓ |
| Learners | `GET /api/learners` | `:8080` | 当前用户 ACTIVE 学习者列表 | 3c ✓ |
| Chat | `POST /api/chat/sessions` | `:8080` | 创建通用会话 | 3c ✓ |
| Chat | `GET /api/chat/sessions` | `:8080` | 会话列表 | 3c ✓ |
| Chat | `GET /api/chat/sessions/{id}/messages` | `:8080` | 消息列表 | 3c ✓ |
| Chat | `POST /api/chat/sessions/{id}/stream` | `:8080` | 流式回复 SSE：`message.delta` / `error` / `done` | 3c ✓ |
| CET | `POST /api/cet/sessions` | cet-tutor-server `:8082` | 开课（同步 JSON：`sessionId`/`planSummary`）；需 Bearer + body.`learnerId` 归属校验 | 1 ✓ |
| CET | `POST /api/cet/sessions/{id}/stream` | `:8082` | Tutor 文本小循环 SSE：`message.delta` / `safety.block` / `error` / `done`（输出先 L2 再分块） | 1 ✓ |
| CET | `POST /api/cet/sessions/{id}/complete` | `:8082` | 结课评测摘要 → COMPLETED | 1 ✓ |
| CET | `GET /api/cet/sessions/{id}/report` | `:8082` | 儿童会话摘要 | 1 ✓ |
| Health | `GET /actuator/health` | 两进程 | 探活 | 1 ✓ |
| Admin | `/api/admin/**` | **仅旁路 manage** | — | — |

鉴权：**家长 JWT**；CET 请求带 `learnerId`；服务端校验 `learner_profile.user_id == JWT.userId`；禁止请求体信任 `userId`。

**开课节奏：** 开课用同步 JSON（含 `planSummary`）；流式用于 Tutor / Chat 轮次。CET stream 在 MVP-2B1 支持 `audioBase64` 与 `audio.tts` / `pronunciation` SSE；Web 录音播放见 MVP-2B2。

---

## 5. 配置分区约定

| 分区 | 存放 | 示例键前缀 |
|---|---|---|
| 模型连接 / Prompt | PostgreSQL | `llm_config`、`prompt_template` |
| Memory / RAG / MCP / Security | `application.yml` + 环境变量 | `kidora.memory.*`、`kidora.mcp.*`、`kidora.cet.*` |
| 密钥 | 环境变量 | DB 加密密钥、JWT secret |

`kidora-agent-server` 与 `cet-tutor-server` 可共用库、分进程配置；端口与 MCP URL 不得写死进业务代码。

---

## 6. 数据存储边界

- **权威结构化存储：** PostgreSQL（与 `kidora-ai-manage` 共享）。
- **知识库向量：** 默认 PGVector；可选 ES 8.15.4（与参考仓策略一致，MVP-5）。
- **学习者语义记忆向量：** 独立表，禁止与课程知识库混写。
- CET 会话计划、轮次评测、报告快照见表设计 [database-design.md](database-design.md)。

---

## 7. 可观测性

- Micrometer Tracing + OTLP（实现期）。
- 关键 span / 指标：`chat.request`、`cet.session`、`cet.tutor.round`、`llm.call`、`mcp.tool`、`safety.check`。
- 业务审计：`llm_call_log`；CET 另有会话事件日志（设计见 database）。

---

## 8. 与参考工程对照

| 参考 | Kidora 对应 |
|---|---|
| `assistant-agent-server` | `kidora-agent-server` |
| `assistant-mcp-server` | `kidora-mcp-server` |
| `voice-text-assistant-agent-server` | `cet-tutor-server`（域不同，独立进程模式相同） |
| `ai-*` | `kidora-*` |
| `wj-assistant-agent-manage` | `kidora-ai-manage` |
