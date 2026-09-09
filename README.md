# Kidora AI

儿童健康与成长方向的 **Agent 平台**运行时仓库。首期产品：**Child English Tutor（CET）** — 儿童英语口语陪练。

> **当前进度：** 阶段 3c 已落地（CET Safety 补洞、通用 Chat SSE、`kidora-web` 文本陪练）。下一阶段：MVP-2 语音 MCP。

## 仓库边界

| 仓库 | 职责 |
|---|---|
| **本仓库 `kidora-ai`** | Chat / CET / MCP / `kidora-*`+`cet-*` 库 / `kidora-web` |
| 旁路 `kidora-ai-manage` | 运营管理台（Admin API + 管理前端） |

## 命名

- `groupId`：`com.wuji.kidora.ai`
- 通用模块：`kidora-*`
- 英文陪练域：`cet-*`
- CET 独立进程：`cet-tutor-server` + `cet-tutor-core`

## 数据库（开发机）

| 项 | 值 |
|---|---|
| 实例 | `127.0.0.1:5432`（与 wuji 同机，**库名不同**） |
| 库 | `kidora_ai` |
| 表空间 | `ts_kidora` → `D:/java-sofeware/PostgreSQL/18/tablespaces/ts_kidora` |
| JDBC | `jdbc:postgresql://127.0.0.1:5432/kidora_ai` |
| 用户 | `postgres` / `1234567890` |

### 初始化（推荐）

```powershell
# 1) 建表空间 + 空库（只跑一次）— 见 schema/bootstrap
# 2) 启动 agent-server：Flyway V1 建表 + V2 seed（演示账号 / CET prompts / llm_config）
$env:JAVA_HOME = "D:\java-sofeware\IntelliJ IDEA 2026.1.1\jbr"
mvn -f kidora-common/pom.xml install
mvn -f kidora-memory/pom.xml install
mvn -f kidora-agent-core/pom.xml install
mvn -f kidora-agent-server/pom.xml -DskipTests package
java -jar kidora-agent-server/target/kidora-agent-server-1.0.0-SNAPSHOT.jar
```

演示登录：`parent1` / `parent123`；学习者 `lrn_demo_amy`。  
JWT / API Key 密钥：`KIDORA_JWT_SECRET`、`KIDORA_API_KEY_SECRET`、`KIDORA_LLM_API_KEY`（覆盖 `llm_config`）。

备用（无 Java）：对已存在的 `kidora_ai` 执行 `schema/all.sql`。**勿**与 Flyway 对同一空库各跑一遍。

### CET 文本陪练（本地）

```powershell
mvn -f cet-tutor-core/pom.xml install
mvn -f cet-tutor-server/pom.xml -DskipTests package
java -jar cet-tutor-server/target/cet-tutor-server-1.0.0-SNAPSHOT.jar
# :8082  — Authorization: Bearer <token from :8080 /api/auth/login>
```

| API | 说明 |
|---|---|
| `POST :8080/api/auth/login` | 家长 JWT |
| `GET  :8080/api/learners` | 学习者列表 |
| `POST :8080/api/chat/sessions` (+ stream) | 通用 Chat |
| `POST :8082/api/cet/sessions` | 开课（同步 planSummary） |
| `POST :8082/api/cet/sessions/{id}/stream` | Tutor SSE（先检后发） |
| `POST :8082/api/cet/sessions/{id}/complete` | 结课评测 |
| `GET  :8082/api/cet/sessions/{id}/report` | 儿童摘要 |

### 前台 kidora-web

```powershell
cd kidora-web
cp .env.local.example .env.local
npm install
npm run dev
# http://localhost:3000  — 演示 parent1 / parent123
```

详设：[docs/database-design.md](docs/database-design.md)、[docs/cet-lesson-flow.md](docs/cet-lesson-flow.md)、[docs/ui-design.md](docs/ui-design.md)。

## 文档入口

| 文档 | 说明 |
|---|---|
| [agents.md](agents.md) | Cursor / 开发者总纲（必读） |
| [docs/](docs/) | 架构与分域设计 |
| [task_plan.md](task_plan.md) | MVP 分期与任务跟踪 |
| [findings.md](findings.md) | 调研沉淀 |
| [progress.md](progress.md) | 进度日志 |

CET 核心叙事：**Plan → Practice → Evaluate → Re-plan**（大循环）+ Tutor 小循环；详见 [docs/cet-tutor-design.md](docs/cet-tutor-design.md)。

## 已落地模块

```
kidora-common / kidora-memory / kidora-agent-core
kidora-agent-server（Auth + Chat + Flyway V1–V4）
cet-tutor-core / cet-tutor-server（CET API + SSE）
kidora-web（登录 / CET 文本陪练 / 最小 Chat）
待做：kidora-mcp-server / kidora-rag / 语音闭环
```

## 技术栈（锁定）

JDK 17 · Spring Boot 3.4.8 · Spring AI 1.1.0 · Spring AI Alibaba 1.1.2.2 · PostgreSQL · Next.js 15（`kidora-web`）

版本表与硬性约束见 [agents.md](agents.md)。
