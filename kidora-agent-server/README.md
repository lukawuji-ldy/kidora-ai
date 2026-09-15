# kidora-agent-server

Kidora **主运行时 Boot 服务**：家长鉴权、通用 Chat、学习者档案、Flyway 数据库迁移。

## 主要功能

- User JWT：注册 / 登录 / 家长资料 / 改密
- 学习者（儿童）CRUD（按 `user_id` 隔离）
- 通用大模型对话（SSE 流式）
- Flyway：共享库 `kidora_ai` 表结构与 seed（演示账号、prompt、llm_config 等）

## 架构位置

```
kidora-web ──► kidora-agent-server (:8080)
                      │
                      ▼
               kidora-agent-core ──► kidora-memory / kidora-common
                      │
                 PostgreSQL
```

本服务**不承载** CET Plan-and-Execute 陪练循环（见 [`cet-tutor-server`](../cet-tutor-server/README.md)），也不暴露 `/api/admin/**`。

## 与 AI Agent 的关系

- 通过 [`kidora-agent-core`](../kidora-agent-core/README.md) 装配有界 ReactAgent / ChatFacade
- 模型连接与 Prompt 读库；调用写入审计日志
- CET 的 Plan → Practice → Evaluate → Re-plan 在 `cet-tutor-*`，此处仅提供账号与可选通用 Chat

## 技术栈

JDK 17 · Spring Boot 3.4.8 · Spring AI / Spring AI Alibaba · WebFlux · PostgreSQL + Flyway

## 最小本地启动

```powershell
mvn -f ../kidora-common/pom.xml install -DskipTests
mvn -f ../kidora-memory/pom.xml install -DskipTests
mvn -f ../kidora-agent-core/pom.xml install -DskipTests
mvn -f pom.xml -DskipTests package
java -jar target/kidora-agent-server-1.0.0-SNAPSHOT.jar
# http://127.0.0.1:8080
```

演示登录：`parent1` / `parent123`（local/dev seed）。

## 相关模块

- 仓库总览：[../README.md](../README.md)
- [`kidora-agent-core`](../kidora-agent-core/README.md) · [`kidora-web`](../kidora-web/README.md)
