# kidora-memory

学习者 **记忆与画像**库：短事实语义记忆、Learner Profile 读写、结课 Memory Action（结构化落库，冲突可解决）。

## 主要功能

- 学习者画像字段读写（水平、偏好等）
- 短 / 长期记忆条目（禁止把全部对话原样写入长期记忆）
- 结课或异步路径的 Memory Action 应用

## 架构位置

```
kidora-agent-core / cet-tutor-core ──► kidora-memory ──► PostgreSQL
```

由 Agent / CET 在开课读画像、结课写记忆；**不**在 Tutor 小循环每句重写全量历史。

## 与 AI Agent 的关系

- 支撑 Plan-and-Execute：**Plan** 可读画像与相关记忆；**结课** 后沉淀结构化事实
- 小循环只读当前计划 + 必要短记忆，控制延迟与隐私面

## 技术栈

JDK 17 · JDBC / Spring 数据访问（随引用方装配）

## 本地安装

```powershell
mvn -f ../kidora-common/pom.xml install -DskipTests
mvn -f pom.xml install -DskipTests
```

## 相关模块

- 仓库总览：[../README.md](../README.md)
- [`kidora-agent-core`](../kidora-agent-core/README.md) · [`cet-tutor-core`](../cet-tutor-core/README.md)
