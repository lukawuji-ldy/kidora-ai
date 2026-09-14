# 后台管理设计 — 归档

> **实现在旁路仓库 [`kidora-ai-manage`](../../kidora-ai-manage)。**  
> 本文件仅保留边界说明；**MVP-1（账号 / LLM / Prompt / llm_call_log）已在 manage 仓落地。**

---

## 1. 目标与边界

| 在 manage 仓库 | 不在本仓库 |
|---|---|
| `/api/admin/**`、Admin JWT（issuer `kidora-admin`） | Chat / CET 运行时 API |
| `llm_config` / Prompt 版本管理 | MCP Tool 进程 |
| MCP Server 注册与工具绑定（MVP-2） | `kidora-web` 用户 UI |
| 知识库运营（MVP-5） | ReactAgent 执行循环 |
| 学习者画像只读运营查询（MVP-3） | |
| 语音供应商主备 + **CET 人设音色**（`cet_persona_voice`） | |
| CET 道具资产与缺失生成审核（`cet_prop_asset` / `cet_prop_asset_generation_task`） | |
| `llm_call_log` 只读；Checkpoint 线程/步骤只读回放；写操作 `admin_audit_log` | |

权威设计与启动步骤：[kidora-ai-manage/docs/admin-design.md](../../kidora-ai-manage/docs/admin-design.md)、[kidora-ai-manage/README.md](../../kidora-ai-manage/README.md)。

人设音色 Admin API：`GET/PUT /api/admin/cet/persona-voices`（详情见 manage 仓 admin-design；表 DDL 在本仓 `schema/23_cet_persona_voice.sql`；Flyway `V11` 腾讯 seed + `V25` 讯飞 seed）。

CET 道具 Admin API：`GET /api/admin/cet/props/assets`、`GET /api/admin/cet/props/tasks`、`POST /api/admin/cet/props/tasks/{taskId}/generate|approve|reject|regenerate`、`POST /api/admin/cet/props/assets/{id}/disable`。生成结果默认待审核；管理台驳回后可创建新版本重新生成。表 DDL 在本仓 `schema/26_cet_prop_asset_generation_task.sql`，运行时由 Flyway `V27` 建表。

Checkpoint 回放 Admin API（manage）：`GET /api/admin/logs/checkpoints/threads`、`.../threads/{threadId}`、`.../{checkpointId}`；DDL 在本仓 `schema/24_agent_checkpoint.sql` / Flyway `V19`。

---

## 2. 协作约定

1. 管理台改 LLM/Prompt/MCP 绑定后，运行时进程需重启或后续跨进程失效。
2. Schema 权威默认在 `kidora-ai`；管理台 **Flyway 关闭**，消费同一库。
3. Admin JWT 与 User JWT **密钥 / issuer 隔离**（`KIDORA_ADMIN_JWT_SECRET` vs `KIDORA_JWT_SECRET`）。
4. 改 Admin API 只更新 manage 仓库 docs；改运行时 API 只更新本仓库 docs；共享表变更**两仓索引互链并同交付说明**。

---

## 3. 文档关系

- 运行时总纲：[agents.md](../agents.md)
- 管理台总纲：`kidora-ai-manage/agents.md`
- 架构：[architecture.md](architecture.md)
