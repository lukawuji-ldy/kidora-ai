# kidora-common

跨模块 **公共类型**库：共享 DTO、错误码与约定，无业务 Agent 逻辑。

## 主要功能

- 统一 API 响应 / 错误码等公共类型
- 供 Boot 服务与 jar 库共同依赖的轻量契约

## 架构位置

```
kidora-agent-server / kidora-mcp-server / cet-tutor-* / kidora-*-core
        └──► kidora-common
```

位于依赖图最底层之一；**不得**反向依赖 `kidora-agent-core`、`cet-tutor-core` 等。

## 与 AI Agent 的关系

无 Plan-and-Execute / ReactAgent 实现；仅为各 Agent 服务提供共享数据结构。

## 技术栈

JDK 17 · 纯 jar（随引用方传递 Jackson 等）

## 本地安装

```powershell
mvn -f pom.xml install -DskipTests
```

## 相关模块

- 仓库总览：[../README.md](../README.md)
