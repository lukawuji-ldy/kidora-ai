# Java / Spring 编码规范

本文档约束本仓库 Java 与 Spring 代码风格、异常、日志与测试。  
模块边界见 [architecture.md](architecture.md)；总纲见 [agents.md](../agents.md)。

> 规范自 MVP-1 起强制执行；新增 / 修改的 Java 源文件必须符合本文。

---

## 1. 语言与基础约定

- Java **17**：优先 Record、密封类型（若合适）、Lambda、Stream、Optional。
- 禁止用全局可变静态字段保存请求级 / 会话级状态。
- 禁止魔法字符串：错误码、角色名、配置键使用常量或枚举。
- 对外 API 使用 WebFlux；若调用同步 Agent / JDBC，必须在有界线程池隔离，**禁止阻塞 event-loop**。

---

## 1.1 Javadoc 与作者标注（强制）

1. **每个** Java 类型（`class` / `interface` / `enum` / `record`，含顶层与嵌套类型）须有类型级 Javadoc。
2. 类型级 Javadoc **必须**包含作者行，固定为：

```java
/**
 * 一句话说明职责。
 *
 * @author liudy
 */
```

3. 公开方法 / 构造器若编写 Javadoc，建议同样在块末写 `@author liudy`（与类型一致）；私有方法可不写 Javadoc，若写了注释块则同样带 `@author liudy`。
4. 禁止省略、篡改或写成其他作者名；联名贡献在类型说明中另述，**不**用其它 `@author` 替换本约定。
5. Cursor / 人工新增文件时默认补齐；Code Review 将缺失 `@author liudy` 视为规范缺陷。

---

## 1.2 数据库 DDL 注释（强制）

1. 每张业务表必须有 `COMMENT ON TABLE ... IS '...'`。
2. **每个字段**必须有 `COMMENT ON COLUMN ... IS '...'`（中文；枚举写清取值；业务外键写明指向）。
3. Schema 权威在仓库根 [`schema/`](../schema/)；已建库用 Flyway **追加**迁移补注释，禁止改已发布的 `V1` 等内容。
4. 新表/新列与注释必须同一次交付；验收见 [database-design.md](database-design.md) §8（`\d+` / 缺注释查询为空）。
5. 细则与示例见 [database-design.md](database-design.md) §2（存储原则第 6 条）与 §6–§8。

---

### 2.1 依赖注入

使用构造器注入；禁止字段 `@Autowired`。

### 2.2 配置

- AI 参数：LLM 连接与 Prompt 以 PostgreSQL 库表为准；Memory/RAG/MCP/CET 等可放 `application.yml`。
- 敏感项禁止提交到 Git；库中存密文，解密密钥用环境变量。
- 禁止代码硬编码模型名与大段系统/用户 Prompt。
- **提示词语言**：`prompt_template` / `prompt_template_version` 的 `name` 与 `content` **须为中文**（种子、管理台编辑与发布均适用）；`{{var}}` 占位符、JSON 键名与枚举（如 `ALLOW`）可保留协议英文。
- 各子工程独立 `pom.xml`（无 parent）；版本必须与 [agents.md](../agents.md) 锁定表一致。
- LLM 依赖：`org.springframework.ai:spring-ai-starter-model-openai`。

### 2.3 模块与包名

| 模块 | 根包 |
|---|---|
| `kidora-agent-server` | `com.wuji.kidora.ai.agent.server` |
| `kidora-mcp-server` | `com.wuji.kidora.ai.mcp` |
| `cet-tutor-server` | `com.wuji.kidora.ai.cet.server` |
| `kidora-agent-core` | `com.wuji.kidora.ai.agent` |
| `kidora-memory` | `com.wuji.kidora.ai.memory` |
| `kidora-rag` | `com.wuji.kidora.ai.rag` |
| `kidora-common` | `com.wuji.kidora.ai.common` |
| `cet-tutor-core` | `com.wuji.kidora.ai.cet.core` |

`groupId`：`com.wuji.kidora.ai`。

---

## 3. 异常

- 对外统一错误体（code / message / requestId）；不向客户端泄漏堆栈与内部表名。
- 业务可预期错误用受控异常；禁止吞异常。
- CET / Safety 拒绝须有明确错误码（如 `CET_SAFETY_BLOCKED`），可审计。

---

## 4. 日志

- 使用结构化日志（含 `requestId`、`sessionId`、`userId` 哈希或内部 id）。
- **禁止**打印 API Key、完整儿童对话原文、身份证/手机号等；完整 prompt 仅写审计表。
- Safety 命中记 warn + 审计，不记敏感原文全文。

---

## 5. 测试

- 核心逻辑必须有单元测试：CET 计划状态迁移、评测 JSON 解析、记忆 Action 冲突、Safety 判定、工具轮次上限。
- WebFlux 控制器可用 WebTestClient；禁止依赖真实外网 LLM 的默认 CI（可用 stub）。
- 测试类同样须有类型级 Javadoc，并标注 `@author liudy`。

---

## 6. AI 依赖

- 优先 Spring AI Alibaba 官方能力；禁止自研 ReAct 主循环与 MCP 协议栈。
- Agent / Tool 必须配置最大轮次 / 最大模型调用次数。

---

## 7. 版本控制（强制）

1. 日常开发在**当前检出分支**直接改代码；默认工作分支为 **`master`**。
2. Agent / 人工**禁止**为本仓库功能新建分支、切换分支、merge/rebase/cherry-pick、或走「功能分支 → PR」流程。
3. 需要落盘时，仅在用户**明确要求提交**时于当前分支执行 `git commit`；禁止擅自 `push --force` 或改写 git config。
4. 旁路仓库（如 `kidora-ai-manage`）各自独立遵守其约定；本规范约束本仓库（`kidora-ai`）工作流。

---

## 8. Cursor / PR 清单

提交前自检：

1. 版本未漂移；无新增未文档化的模块依赖方向。
2. 无硬编码 Prompt / 模型名 / 密钥。
3. 相关 docs 已同步。
4. CET 改动未破坏「小循环不重规划」约束。
5. **新增 / 修改的 Java 类型均含 `@author liudy`。**
6. **新增 / 修改的表与字段均有 `COMMENT ON`（见 database-design）。**
7. **未新建/切换功能分支**（见 §7）。

---

## 9. 文档强制同步

凡改动下列任一项，**必须**同一次交付更新 `agents.md` 与对应 `docs/`：

- 对外 API / SSE 事件
- 包或类的对外职责
- 表结构 / 迁移
- 配置项键名
- 鉴权约定
- 模块依赖方向
- 编码规范（含 Javadoc 作者约定）

禁止只改代码不改文档。
