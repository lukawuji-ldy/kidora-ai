# kidora-agent-core

平台 **Agent 能力库**：模型路由、可配置 Prompt、调用审计、通用 Chat 门面与 Agent 工厂（有界 ReactAgent + Checkpoint）。

## 主要功能

- ModelRouter：按库表 `llm_config` 装配 OpenAI Compatible 客户端
- Prompt 模板加载与渲染（库表版本化；业务正文中文）
- LLM 调用审计（完整入模参数入库，应用日志不落 Key / 儿童隐私全文）
- ChatFacade / AgentFactory：通用对话与工具循环（**必须**最大执行次数）
- 供 CET、管理台等复用的横切能力

## 架构位置

```
kidora-agent-server ──┐
cet-tutor-core     ──┼──► kidora-agent-core ──► kidora-memory / kidora-common
kidora-agent-manage──┘         （旁路 manage 经 Maven install 引用）
```

本模块为 jar，不可独立启动。

## 与 AI Agent 的关系

- **通用 Chat**：有界 ReactAgent；与 CET **Plan-and-Execute** 大循环职责分离
- **CET**：`cet-tutor-core` 的 Planner / Tutor / Evaluator 通过本库访问模型与 Prompt，**不**在本库内实现课时状态机
- 禁止自研无限 ReAct 主循环；优先官方 Agent Framework

## 技术栈

JDK 17 · Spring AI 1.1.0 · Spring AI Alibaba Agent Framework

## 本地安装（被 Boot 依赖）

```powershell
mvn -f ../kidora-common/pom.xml install -DskipTests
mvn -f ../kidora-memory/pom.xml install -DskipTests
mvn -f pom.xml install -DskipTests
```

## 相关模块

- 仓库总览：[../README.md](../README.md)
- [`kidora-agent-server`](../kidora-agent-server/README.md) · [`cet-tutor-core`](../cet-tutor-core/README.md)
