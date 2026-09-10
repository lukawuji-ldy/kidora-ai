# Kidora AI 平台 / Child English Tutor — Agent 开发指导文档

本文档是 Cursor / 开发者的**总纲**：只保留目标、版本锁定、模块边界、硬性约束与文档索引。  
可编码级细节一律见 [`docs/`](docs/)。调研沉淀见 [`findings.md`](findings.md)；分期跟踪见 [`task_plan.md`](task_plan.md)。

> **仓库定位：** 本仓库（`kidora-ai`）承载**运行时**：通用 Chat、CET 陪练、MCP Server、`kidora-*` / `cet-*` 库与前台 Web。运营管理台在旁路仓库 `kidora-ai-manage`。

---

## 1. 项目目标

构建儿童健康与成长方向的 **Kidora Agent 平台**；首期产品为 **Child English Tutor（CET）** 儿童英语口语陪练。

平台能力（目标全景）：

- 通用大模型对话（OpenAI Compatible）与多产品线挂载预留
- CET：Plan → Practice → Evaluate → Re-plan（大循环）+ Tutor 实时小循环
- 儿童 Safety Guard（独立系统约束）
- 学习者短/长期记忆与画像（Learner Profile）
- MCP 工具：ASR / TTS / 发音评测 / 词典 / 安全检测等（分期）
- 课程内容知识库（`kidora-rag`，分期）
- 家长可读学习报告与 Human-in-the-loop（分期）
- 全链路可观测（OpenTelemetry）

---

## 2. 技术栈与版本锁定

| 技术 | 版本 |
|---|---|
| Java | JDK 17 |
| Spring Boot | 3.4.8 |
| Spring AI Alibaba | 1.1.2.2 |
| Spring AI Alibaba Extensions | 1.1.0.0 |
| Spring AI | 1.1.0 |
| OpenTelemetry | 1.35.0 |
| 关系库 | PostgreSQL（与管理工程共享） |
| 知识库检索向量（二选一） | PGVector（默认）或 Elasticsearch **8.15.4** |
| 前台 | Next.js（`kidora-web`） |

**版本锁定原则**

- **不设置**仓库级 Maven parent POM / 多模块 reactor；各子工程**各自独立 `pom.xml`**。根目录现有 `pom.xml` 仅为非聚合 stub。
- 各子工程依赖版本**必须统一**对齐上表；编码时**不得擅自漂移大版本**。
- 大模型接入统一 OpenAI Compatible：`spring-ai-starter-model-openai`。
- 实现 API 用法参考旁路 `spring-ai-alibaba-examples`（若本地有），**跟用法、不跟 examples 的 Boot/SAA 版本**。
- 优先使用 Spring AI Alibaba 官方能力，禁止重复造 ReAct 循环或 MCP 协议栈。

**核心依赖（按能力）**

| 能力 | 依赖 |
|---|---|
| 大模型 | `org.springframework.ai:spring-ai-starter-model-openai` |
| Agent | `spring-ai-alibaba-agent-framework` |
| MCP Client | `spring-ai-starter-mcp-client-webflux` |
| MCP Server | `spring-ai-starter-mcp-server-webflux` |
| 向量（默认） | PGVector 相关 Spring AI starter（实现期按版本表引入） |

**命名约定**

- `groupId` 统一：`com.wuji.kidora.ai`
- **通用模块**前缀：`kidora-*`
- **英文陪练域**前缀：`cet-*`

---

## 3. 模块划分与依赖规则

各子工程独立 Maven 工程（**无 parent 聚合**）；目标树如下（**已落地含 `kidora-mcp-server` + CET MCP Client**）：

```
kidora-ai/                     # 仓库根（文档 + 子工程，不是 parent pom）
├── schema/                    # DDL 权威 + bootstrap（表空间/空库）
├── kidora-agent-server/       # Boot：Auth / Chat / Flyway
├── kidora-mcp-server/         # Boot：MCP 工具服务（stub|db 路由讯飞/腾讯）
├── cet-tutor-server/          # Boot：CET 陪练 API / SSE
├── kidora-agent-core/         # jar：Agent 编排、工具装配、模型路由
├── kidora-memory/             # jar：短/长期记忆、学习者画像
├── kidora-rag/                # jar：课程/内容知识库（MVP-5）
├── kidora-common/             # jar：公共 DTO / 错误码
├── cet-tutor-core/            # jar：Planner / Tutor / Eval / Re-plan / Safety
└── kidora-web/                # Next.js 前台（npm），不纳入 Maven
```

**依赖方向（禁止反向、禁止环）**

```
kidora-agent-server ──► kidora-agent-core ──► kidora-memory / kidora-rag / kidora-common

cet-tutor-server ──► cet-tutor-core ──► kidora-agent-core ──► kidora-memory / kidora-rag / kidora-common

kidora-mcp-server ──► kidora-common
（禁止依赖 kidora-agent-core / kidora-memory / kidora-rag / cet-tutor-core）
```

| 模块 | 类型 | 一句话职责 |
|---|---|---|
| `kidora-agent-server` | Boot | Auth JWT、通用 Chat SSE、`/api/learners`、Flyway |
| `kidora-mcp-server` | Boot | 按 MCP 规范暴露 Tool，独立部署（:8081；`stub|azure`） |
| `cet-tutor-server` | Boot | CET 开课/陪练/报告 API；装配 `cet-tutor-core` |
| `kidora-agent-core` | jar | ModelRouter / Prompt / 审计 / ChatFacade（AgentFactory 空壳） |
| `kidora-memory` | jar | 学习者画像读写、语义记忆短事实、结课 Memory Action |
| `kidora-rag` | jar | 课程文档入库与检索（分期） |
| `kidora-common` | jar | 跨模块公共类型与约定 |
| `cet-tutor-core` | jar | CET 大/小循环编排与结构化计划/评测 / Safety / Re-plan |
| `kidora-web` | Next.js | 登录、首页、CET 文本/语音陪练、最小 Chat |

运营管理台：旁路 `kidora-ai-manage`（`kidora-agent-manage` + `kidora-admin-web`）。本仓库**不暴露** `/api/admin/**`。

详情：[docs/architecture.md](docs/architecture.md)

---

## 4. 硬性约束

1. **模型接入**：统一 OpenAI Compatible；禁止代码硬编码模型名 / API Key。LLM 连接参数入库 PostgreSQL（`llm_config`，区分 `CHAT` | `EMBEDDING`）。管理台更新后，本仓库运行时进程需重启或后续跨进程失效方可加载新配置。
2. **Agent**：优先官方 Agent Framework / Workflow；禁止自研 ReAct 主循环；**必须设置最大执行次数**，禁止无限循环。
3. **CET 循环边界**：Plan-and-Execute **仅**用于课程/训练「大循环」；实时陪练走 Tutor「小循环」。**禁止**孩子每说一句就同步完整 Re-plan。细则见 [docs/cet-tutor-design.md](docs/cet-tutor-design.md)。
4. **Safety**：儿童场景必须有独立 Safety 闸门（输入/输出），禁止仅靠系统 Prompt 一句「你是儿童老师」。见 [docs/cet-safety-design.md](docs/cet-safety-design.md)。
5. **MCP**：Server 独立进程；与 Agent 服务无强耦合；Client 使用 WebFlux + SSE。跨 Server 工具名冲突 fail-fast。见 [docs/mcp-design.md](docs/mcp-design.md)。
6. **对外 API**：WebFlux；流式用 SSE。阻塞 LLM/JDBC 必须在有界线程池隔离，**禁止阻塞 event-loop**。身份以 **User JWT** 为准，**禁止信任前端传入的 userId**。本仓库不暴露 `/api/admin/**`。
7. **存储**：结构化数据一律 **PostgreSQL**（与管理工程共享库 **`kidora_ai`**，表空间 **`ts_kidora`**；开发 JDBC 见 [docs/database-design.md](docs/database-design.md)）。用户/学习者数据按 `user_id`（及儿童档案 id）隔离。**不使用 MySQL。**
8. **记忆**：禁止把全部对话原样写入长期记忆；以结构化 Action + 冲突解决落库。Learner Profile 字段约定见 [docs/agent-memory.md](docs/agent-memory.md)。
9. **入模审计**：每次 LLM 调用完整参数写入审计表（如 `llm_call_log`）。
10. **提示词**：系统提示词与模板**配置化**（库表 + 版本），禁止业务代码硬编码大段 Prompt。含 CET Planner/Tutor/Eval/Safety 相关 prompt group。**正文与展示名须使用中文**（管理台新建/发布同样遵守）；`{{变量}}`、结构化输出的 JSON 键名与枚举值可保持协议英文。
11. **配置**：运行参数可放 `application.yml`；模型连接与 Prompt 以库为准；敏感项用环境变量。
12. **可观测**：OpenTelemetry 覆盖请求、Agent、LLM、MCP、CET 会话轮次与 Token。
13. **安全日志**：禁止在应用日志打印 API Key、儿童隐私、完整敏感对话；完整 prompt 仅进审计表。
14. **工程结构**：无 parent POM；子工程独立 pom，版本与本文档锁定表一致。`groupId` 固定 `com.wuji.kidora.ai`。
15. **文档同步（强制）**：凡改对外 API、包/类职责、表结构、配置项、SSE 事件、鉴权约定，**必须同一次交付内更新** [`agents.md`](agents.md) / [`docs/`](docs/) 对应章节。细则见 [docs/coding-standard.md](docs/coding-standard.md)。
16. **DDL 注释（强制）**：业务表与字段必须有 PostgreSQL `COMMENT ON`；见 [docs/database-design.md](docs/database-design.md) §2 / §8。

---

## 5. Cursor 开发规则

生成或修改代码时必须遵守（**MVP-1 起适用；MVP-0 仅文档**）：

1. 优先对照 `spring-ai-alibaba-examples` 中同类能力模块的官方 API。
2. 使用官方 Starter / Agent Framework，不随意引入第三方 AI 框架。
3. 保证可编译运行；子工程独立 pom，版本对齐本文档；LLM/Prompt 可配置（库表）。
4. 仅 `kidora-agent-server`、`kidora-mcp-server`、`cet-tutor-server` 作为启动入口。
5. 核心逻辑（CET 计划状态机、评测结构化输出、记忆 Action、工具轮次上限、Safety）必须有单元测试。
6. 遵循 [docs/coding-standard.md](docs/coding-standard.md)（含 **Javadoc `@author liudy`** 强制约定）。
7. 改动前先读相关 docs；**改动后同步更新 docs**。
8. 新增 / 修改 Java 类型时类型级 Javadoc 必须含 `@author liudy`。
9. **版本控制**：在当前检出分支（默认 `master`）直接开发与提交；**禁止**新建/切换功能分支、merge/rebase/cherry-pick 或 PR 分支流。细则见 [docs/coding-standard.md](docs/coding-standard.md) §7。
10. **Cursor 模型选用（实现阶段）**：
   - **UI / 前端（`kidora-web`）**：指定使用 `Gemini 3 Flash`（若环境可用）
   - **其它环节**：使用 `auto`

---

## 6. 文档索引

| 文档 | 用途 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 部署拓扑、模块依赖、主链路、配置分区 |
| [docs/ui-design.md](docs/ui-design.md) | `kidora-web` 信息架构与 CET 陪练交互 |
| [docs/admin-design.md](docs/admin-design.md) | Admin 归档；实现见旁路 `kidora-ai-manage` |
| [docs/agent-flow.md](docs/agent-flow.md) | 通用 ReactAgent、SSE、审计、失败策略 |
| [docs/agent-memory.md](docs/agent-memory.md) | 短/长期记忆与 Learner Profile |
| [docs/rag-design.md](docs/rag-design.md) | 课程知识库（目标设计，MVP-5） |
| [docs/database-design.md](docs/database-design.md) | PostgreSQL 连接/表空间/MVP-1 表与迁移约定 |
| [docs/coding-standard.md](docs/coding-standard.md) | Java / Spring / 日志 / 测试 / Javadoc `@author liudy` / 文档同步 / **版本控制（禁功能分支）** |
| [docs/mcp-design.md](docs/mcp-design.md) | MCP Server、CET 工具清单与分期 |
| [docs/cet-tutor-design.md](docs/cet-tutor-design.md) | CET 总设计：大/小循环与多 Agent |
| [docs/cet-lesson-flow.md](docs/cet-lesson-flow.md) | 开课到结课的课时流程 |
| [docs/cet-safety-design.md](docs/cet-safety-design.md) | 儿童安全闸门与 HITL |
| [docs/cet-assessment-design.md](docs/cet-assessment-design.md) | 评测维度与家长报告 |
| [docs/cet-persona-design.md](docs/cet-persona-design.md) | AI 外教人设 |
| [findings.md](findings.md) | 调研沉淀 |
| [task_plan.md](task_plan.md) | 分期与任务跟踪 |
| [progress.md](progress.md) | 进度日志 |

---

## 7. 后续扩展与 MVP 分期

**扩展方向**：更多 Kidora 产品线（非英语）、Agent Skill、基于审计日志的评测产品、A2A、知识图谱、细粒度家长/老师权限。

**MVP 分期**

| 阶段 | 范围 | 明确不做 |
|---|---|---|
| **MVP-0** | 双仓库 agents + docs + 计划文件 | 任何业务编码 |
| **MVP-1 DB** | 库 `kidora_ai` / 表空间 `ts_kidora` / DDL / 最小 `kidora-agent-server` | CET 业务逻辑 |
| **MVP-1** | Safety + Planner + Tutor 文本陪练 + 会话评测摘要（API 已落地） | 每轮 Re-plan；完整 ASR/发音 MCP |
| **3c** | CET Safety 补洞 + 通用 Chat + `kidora-web` 文本陪练 | MCP 语音 |
| **MVP-2A** | `kidora-mcp-server` stub + `mcp_*` 表 + Azure F0 选型 | 真实供应商 / CET Client / Web 语音 |
| **MVP-2B1** | Azure REST + CET MCP Client + stream SSE | Web 录音/播放 |
| **MVP-2C** | 讯飞/腾讯整栈 + 管理台主备（无自动切换） | Azure 暂不用；无自动 failover |
| **MVP-2B2** | kidora-web 录音/播放闭环 | 课程 RAG 生产化 |
| **MVP-2**（总） | MCP：ASR / TTS / 发音评测；语音闭环 | 课程 RAG 生产化 |
| **MVP-3** | Evaluator 驱动 Re-Planner；长期 Learner Profile；词典 MCP | 完整家长 HITL 产品 |
| **MVP-4** | 家长报告 + HITL；管理台 CET 运营页 | — |
| **MVP-5** | `kidora-rag`；更多产品线挂载 | — |

后台管理：旁路 `kidora-ai-manage`。
