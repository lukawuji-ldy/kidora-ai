# cet-tutor-server

CET **陪练 API 服务**：开课、Tutor SSE、结课评测与家长报告；装配 [`cet-tutor-core`](../cet-tutor-core/README.md)。

## 主要功能

- `POST /api/cet/sessions`：开课（同步产出计划摘要，大循环 **Plan**）
- `POST /api/cet/sessions/{id}/stream`：Tutor 小循环 SSE（可含 ASR / TTS / `turn.prop` 等事件）
- 结课评测与家长可读报告
- 会话删除 / 批量删除
- 可选 MCP Client（语音与发音）

## 架构位置

```
kidora-web ──► cet-tutor-server (:8082)
                      │
                      ├──► cet-tutor-core（Plan-and-Execute + Tutor + Safety）
                      ├──► kidora-agent-core / kidora-memory
                      └──► kidora-mcp-server（可选）
```

身份以 User JWT 为准（由 `kidora-agent-server` 签发）；禁止信任前端传入的 userId。

## 与 AI Agent 的关系（Plan-and-Execute）

| API 阶段 | Agent 行为 |
|---|---|
| 开课 | **Planner** 生成 `TrainingPlan`（大循环 Plan） |
| stream 陪练 | **Tutor** 小循环执行；**不**每轮完整 Re-plan |
| 阶段 / 结课 | **Evaluator**；必要时 **RePlanner**（大循环 Re-plan） |
| 全程 | **Safety** 先检后发 |

叙事全称：**Plan → Practice → Evaluate → Re-plan**。细节见 [`cet-tutor-core`](../cet-tutor-core/README.md)。

## 技术栈

JDK 17 · Spring Boot 3.4.8 · WebFlux SSE · Spring AI Alibaba

## 最小本地启动

先启动 [`kidora-agent-server`](../kidora-agent-server/README.md)（鉴权 + 库表），语音场景再启 [`kidora-mcp-server`](../kidora-mcp-server/README.md)。

```powershell
mvn -f ../kidora-common/pom.xml install -DskipTests
mvn -f ../kidora-memory/pom.xml install -DskipTests
mvn -f ../kidora-agent-core/pom.xml install -DskipTests
mvn -f ../cet-tutor-core/pom.xml install -DskipTests
mvn -f pom.xml -DskipTests package
java -jar target/cet-tutor-server-1.0.0-SNAPSHOT.jar
# http://127.0.0.1:8082  — Authorization: Bearer <token from :8080>
```

## 相关模块

- 仓库总览：[../README.md](../README.md)
- [`cet-tutor-core`](../cet-tutor-core/README.md) · [`kidora-web`](../kidora-web/README.md)
