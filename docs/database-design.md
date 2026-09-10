# 数据库设计（PostgreSQL）

共享库连接、表空间、MVP-1 表结构与迁移约定。  
**Schema 权威在本仓库**（`schema/` + `kidora-agent-server` Flyway）；旁路 `kidora-ai-manage` 只消费，不另建冲突 DDL。

---

## 1. 连接与物理布局（已落地）

| 项 | 值 |
|---|---|
| Host / Port | `127.0.0.1:5432`（与 `wuji-assistant-agent` 同实例） |
| 用户 / 密码 | `postgres` / `1234567890`（开发默认） |
| 数据库 | **`kidora_ai`**（**不是** `vector_test`） |
| JDBC | `jdbc:postgresql://127.0.0.1:5432/kidora_ai` |
| 表空间 | **`ts_kidora`** |
| 表空间目录 | `D:/java-sofeware/PostgreSQL/18/tablespaces/ts_kidora` |
| 本机 psql | `D:\java-sofeware\PostgreSQL\18\bin\psql.exe` |

库默认表空间为 `ts_kidora`；业务表经 Flyway 创建后均落在该表空间。

### 1.1 引导顺序（推荐）

1. 创建空目录并授权服务账号可写（见 [`schema/bootstrap/README.md`](../schema/bootstrap/README.md)）。
2. 连 `postgres` 库执行 [`schema/bootstrap/01_tablespace_and_db.sql`](../schema/bootstrap/01_tablespace_and_db.sql)（**只**建表空间 + 空库）。
3. 启动 `kidora-agent-server`，由 **Flyway** `V1__init.sql` 建表。

[`schema/all.sql`](../schema/all.sql) 为无 Java 时的备用一键建表；**禁止**对同一空库既跑 `all.sql` 又跑 Flyway。

---

## 2. 存储原则

1. 结构化业务数据与管理元数据一律 PostgreSQL；**不使用 MySQL**。
2. 多租户/用户隔离：`user_id`；儿童学习者使用 `learner_id`（归属家长 `user_id`）。
3. 向量：默认 PGVector（表与扩展延后到 MVP-3/5）；本切片仅启用 `pgcrypto`。
4. 软删除优先（`deleted`）；审计表只增不改。
5. 命名：`snake_case`；时间 `timestamptz`；主键统一 **`BIGINT`（雪花）+ 业务 `VARCHAR` 键**。
6. **表与字段注释（强制）**：每一张业务表必须有 `COMMENT ON TABLE`；**每一个字段**必须有 `COMMENT ON COLUMN`（中文说明职责；枚举字段写清取值）。权威在 [`schema/*.sql`](../schema/)；已建库通过 Flyway 追加迁移补齐（如 `V3__complete_table_column_comments.sql`）。禁止只建表不写注释。

---

## 3. 平台业务表（MVP-1 已建）

| 表 | SQL 源 | 用途 |
|---|---|---|
| `app_user` | [`01_app_user.sql`](../schema/01_app_user.sql) | 前台登录用户（家长/老师） |
| `learner_profile` | [`02_learner_profile.sql`](../schema/02_learner_profile.sql) | 儿童学习者基础档案 |
| `llm_config` | [`03_llm_config.sql`](../schema/03_llm_config.sql) | 模型连接（`CHAT` \| `EMBEDDING`） |
| `prompt_template` | [`04_prompt_template.sql`](../schema/04_prompt_template.sql) | 提示词线上副本（`name`/`content` 须中文） |
| `prompt_template_version` | [`05_prompt_template_version.sql`](../schema/05_prompt_template_version.sql) | 提示词版本历史（同上） |
| `chat_session` | [`06_chat_session.sql`](../schema/06_chat_session.sql) | 通用聊天会话 |
| `chat_message` | [`07_chat_message.sql`](../schema/07_chat_message.sql) | 通用聊天消息 |
| `llm_call_log` | [`08_llm_call_log.sql`](../schema/08_llm_call_log.sql) | 入模审计；`biz_source`：`CHAT`\|`CET` |
| `admin_user` | [`09_admin_user.sql`](../schema/09_admin_user.sql) | 后台账号（管理台写） |
| `admin_audit_log` | [`10_admin_audit_log.sql`](../schema/10_admin_audit_log.sql) | 管理操作审计 |
| `mcp_server_ref` | [`17_mcp_server_ref.sql`](../schema/17_mcp_server_ref.sql) | MCP Server 注册（ACTIVE 供 Client） |
| `mcp_tool_binding` | [`18_mcp_tool_binding.sql`](../schema/18_mcp_tool_binding.sql) | 工具按产品线绑定（CET/CHAT） |
| `speech_vendor_config` | [`19_speech_vendor_config.sql`](../schema/19_speech_vendor_config.sql) | 语音供应商凭证（讯飞/腾讯/Azure） |
| `speech_route` | [`20_speech_route.sql`](../schema/20_speech_route.sql) | 语音主备路由（仅 primary 生效） |
| `cet_training_plan_revision` | [`21_cet_training_plan_revision.sql`](../schema/21_cet_training_plan_revision.sql) | 计划修订历史（MVP-3） |
| `learner_semantic_memory` | [`22_learner_semantic_memory.sql`](../schema/22_learner_semantic_memory.sql) | 语义短事实（MVP-3；无强制 embedding） |

**后续：** `user_profile`（通用家长画像，分期）；`kb_*`（MVP-5，见 [rag-design.md](rag-design.md)）。

Flyway：`V6__mcp_registry.sql` 建表并 seed 本地 MCP；`V7__speech_vendor.sql` seed 讯飞主 / 腾讯备 / Azure 第三档；`V8__mvp3_replan_memory.sql`：`cet_training_plan_revision` + `learner_semantic_memory` + 词典绑定 + Re-plan 提示词。

---

## 4. CET 域表（MVP-1 已建）

| 表 | SQL 源 | 用途 |
|---|---|---|
| `cet_lesson_session` | [`11_cet_lesson_session.sql`](../schema/11_cet_lesson_session.sql) | 一次陪练会话 |
| `cet_training_plan` | [`12_cet_training_plan.sql`](../schema/12_cet_training_plan.sql) | 当前生效计划 JSON |
| `cet_tutor_turn` | [`13_cet_tutor_turn.sql`](../schema/13_cet_tutor_turn.sql) | Tutor 小循环轮次 |
| `cet_turn_assessment` | [`14_cet_turn_assessment.sql`](../schema/14_cet_turn_assessment.sql) | 评测结果（MVP-1 会话摘要） |
| `cet_session_report` | [`15_cet_session_report.sql`](../schema/15_cet_session_report.sql) | 结课报告快照 |
| `cet_safety_event` | [`16_cet_safety_event.sql`](../schema/16_cet_safety_event.sql) | Safety 命中事件 |

**后续：** 细粒度轮次评测字段充实；家长报告打磨（MVP-4）。`cet_training_plan_revision` / `learner_semantic_memory` 已落地（见上表）。

### 4.1 `cet_lesson_session` 状态机

`CREATED` → `PLANNING` → `PRACTICING` → `EVALUATING` → (`REPLANNING` → `PRACTICING`)* → `COMPLETED` | `ABORTED` | `SAFETY_BLOCKED`

### 4.2 计划 / 评测 JSON 最小字段

与实现前设计一致，存于 `cet_training_plan.plan_json` / `cet_turn_assessment.assessment_json`：

```json
{
  "topic": "介绍我的宠物",
  "cefr": "A1",
  "personaId": "emma",
  "objectives": {
    "vocabulary": ["pet", "dog", "cute"],
    "patterns": ["I have a..."],
    "grammarFocus": ["present_simple"],
    "targetTurns": 5
  },
  "stages": [
    { "id": "warmup", "goal": "热身" },
    { "id": "vocab", "goal": "关键词" },
    { "id": "dialog", "goal": "对话练习" }
  ]
}
```

```json
{
  "grammar": { "score": 60, "problem": "第三人称单数" },
  "vocabulary": { "score": 90 },
  "pronunciation": { "score": 85 },
  "fluency": { "score": 70 },
  "encouragement": "Great try! Let's practice he/she + has."
}
```

---

## 5. 向量存储（未建表）

| 用途 | 存放 | MVP |
|---|---|---|
| 课程知识库 chunk embedding | `kb_chunk.embedding` 或 ES 投影 | 5 |
| 学习者语义记忆 | `learner_semantic_memory` + embedding | 3 |

禁止知识库失败时静默混用另一后端。

---

## 6. 迁移约定

1. 权威 DDL：仓库根 [`schema/`](../schema/) 分文件；运行时镜像 [`kidora-agent-server/.../db/migration/`](../kidora-agent-server/src/main/resources/db/migration/)（`V1` 建表，`V2` 种子，`V3` 补齐表/字段 COMMENT）。
2. 迁移脚本单调递增；禁止改已发布脚本，只追加。
3. Prompt / LLM 种子数据与 schema 分离或明确标注环境。
4. 管理台与运行时共享库；**schema 权威在 `kidora-ai`**。
5. **bootstrap ≠ 建表**：`schema/bootstrap/` 只建表空间与空库；业务表仅 Flyway（或互斥的 `all.sql`）。
6. 新表 / 新列交付时，**同一次迁移**内写完 `COMMENT ON TABLE` / `COMMENT ON COLUMN`；评审以「`\d+ table` 无空注释」为通过条件。

---

## 7. 命名惯例

- 表：业务前缀 `cet_` / 平台无前缀或后续 `kb_`。
- 索引：`idx_{table}_{cols}`。
- 唯一约束：`uk_{table}_{cols}`。
- 注释：表注释说明业务含义；列注释说明语义、枚举取值、外键指向（业务键名即可）。

---

## 8. 注释验收（psql）

```sql
-- 缺表注释
SELECT c.relname
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind = 'r'
  AND c.relname NOT LIKE 'flyway%'
  AND obj_description(c.oid, 'pg_class') IS NULL;

-- 缺字段注释
SELECT c.relname AS table_name, a.attname AS column_name
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a ON a.attrelid = c.oid
WHERE n.nspname = 'public' AND c.relkind = 'r'
  AND c.relname NOT LIKE 'flyway%'
  AND a.attnum > 0 AND NOT a.attisdropped
  AND col_description(c.oid, a.attnum) IS NULL
ORDER BY 1, a.attnum;
```

两查询结果均应为空。