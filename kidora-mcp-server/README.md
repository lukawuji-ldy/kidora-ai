# kidora-mcp-server

独立部署的 **MCP 工具服务**：向 CET / Agent 暴露语音与评测类 Tool，与陪练编排进程解耦。

## 主要功能

- 按 MCP 规范提供 Tool（WebFlux + SSE）
- 典型工具：`echo_ping`、`asr_transcribe`、`tts_synthesize`、`pronunciation_score`
- 供应商路由（如 stub / 讯飞 / 腾讯等，以运行配置为准）
- 可选 API Key 鉴权

## 架构位置

```
cet-tutor-server ── MCP Client ──► kidora-mcp-server (:8081)
                                         │
                                         ▼
                                    语音/评测供应商 API
```

仅依赖 [`kidora-common`](../kidora-common/README.md)；**禁止**依赖 `kidora-agent-core` / `cet-tutor-core`。

## 与 AI Agent 的关系

- **不执行** Plan-and-Execute；只在 Tutor 小循环或开场等路径被 Client 调用
- Agent 侧将工具结果纳入反馈 / TTS，规划仍由 Planner / RePlanner 负责

## 技术栈

JDK 17 · Spring Boot 3.4.8 · Spring AI MCP Server WebFlux

## 最小本地启动

```powershell
mvn -f ../kidora-common/pom.xml install -DskipTests
mvn -f pom.xml -DskipTests package
java -jar target/kidora-mcp-server-1.0.0-SNAPSHOT.jar
# http://127.0.0.1:8081
```

CET 侧开启 Client：`KIDORA_MCP_ENABLED=true`（连接信息可读库表，空库可回落本机 `:8081`）。

## 相关模块

- 仓库总览：[../README.md](../README.md)
- 调用方：[`cet-tutor-server`](../cet-tutor-server/README.md)
