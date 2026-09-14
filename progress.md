# 进度日志

## 会话：2026-09-13 Checkpoint 存储与后台跟踪

### 方案 1（ReactAgent + PostgresSaver + 管理台回放）
- **状态：** complete
- 执行的操作：
  - `schema/24_agent_checkpoint.sql` + Flyway `V19__agent_checkpoint.sql`（`GraphThread` / `GraphCheckpoint`）
  - `CheckpointSaverFactory` + `KidoraAgentProperties.checkpoint`；`AgentFactory` 实装有界 ReactAgent（本期无工具）
  - `ChatFacade` → `ReactAgent.streamMessages`，`threadId=userId:sessionId`；保留 `chat_message`
  - manage：`/api/admin/logs/checkpoints/**` + `CheckpointsView`（`/logs/checkpoints`）
  - docs：agent-flow / database-design / architecture / admin-design / AGENTS
- 验收：`CheckpointSaverFactoryTest` / `AgentFactoryTest` / `AgentStreamBridgeTest`；manage `AdminCheckpoint*Test` 绿
- **下一步：** 启动 agent-server 跑通一次 Chat 后在管理台验证线程回放；可选挂 MCP 工具环

## 会话：2026-09-10（续）MVP-3 + 词典 + MVP-2 收口

### Phase 0–4
- **状态：** complete
- 执行的操作：
  - master 提交 MVP-2 讯飞/腾讯 WS + Web WavRecorder；coding-standard / AGENTS 写入「禁止功能分支」
  - MCP `dictionary_lookup`（stub|http）+ 绑定 seed + 单测
  - Flyway V8：`cet_training_plan_revision` / `learner_semantic_memory` / Re-plan 提示词
  - `SessionEvaluator.decision` + `LessonReplanner`；阶段 `targetTurns` 触发 Re-plan；SSE `plan.updated`
  - 结课 `LearnerMemoryService` 写 `extra_json` + semantic；Planner 注入画像/记忆摘要
  - docs / task_plan 同步
- 验收：kidora-memory / mcp DictionaryTools / cet-tutor-core / CetSessionControllerTest 绿
- **下一步：** MVP-4 家长报告 / HITL

## 会话：2026-09-10 MVP-2B2 Web 录音/播放 + 语音收尾

### MVP-2B2 + 语音收尾
- **状态：** complete
- 执行的操作：
  - 核对双仓讯飞/腾讯（MP3→`lame`、m4a 拒收、WS 拼帧等）；`SpeechProvider` Javadoc 更新；mcp / manage 相关单测绿
  - `kidora-web`：`WavRecorder`（16k mono WAV）+ `postSse` 消费 `audio.tts` / `pronunciation`
  - CET 会话页：录音/文本并存；儿童气泡「（语音）」；TTS 自动播放；发音四维分；`referenceText`=上一句 Tutor
  - docs：ui-design / mcp-design / task_plan / progress
- 验收：mcp IFlytek/Tencent/Routing test 绿；manage probe test 绿；kidora-web lint + tsc 绿
- **下一步：** 词典等扩展或 MVP-3

## 会话：2026-09-09（续 8）MVP-2C 讯飞/腾讯主备

### MVP-2C
- **状态：** complete
- 执行的操作：
  - `SecretCipher` 下沉 `kidora-common`；`ApiKeyCipherService` 委托
  - Flyway `V7__speech_vendor.sql` + schema 19/20；seed primary=iflytek / backup=tencent / tertiary=azure
  - mcp-server：`IFlytekSpeechProvider` / `TencentSpeechProvider` / `RoutingSpeechProvider`（仅 primary）；`mode=stub|db`
  - manage：`/api/admin/speech/**` + `SpeechVendorsView` 主备与凭证
  - docs：findings / mcp-design / database-design / task_plan
- 验收：common/mcp/manage 相关 test 绿（默认 stub）
- **下一步：** MVP-2B2 Web 录音/播放

## 会话：2026-09-09（续 7）MVP-2B1 Azure + CET MCP Client

### MVP-2B1
- **状态：** complete
- 执行的操作：
  - `kidora-mcp-server`：`SpeechProvider`（Stub / Azure REST）；`kidora.speech.provider`；缺 Key → `AZURE_NOT_CONFIGURED`
  - `cet-tutor-core`：`SpeechToolPort` / `TurnInput` / `CetStreamEvent`；streamTurn ASR→Safety→Tutor→TTS/发音
  - `cet-tutor-server`：MCP Client（`mcp_server_ref` + yml 兜底）；`McpSpeechToolAdapter`；SSE `audio.tts` / `pronunciation`
  - docs：mcp-design / cet-lesson-flow / architecture / task_plan / AGENTS
- 验收：mcp-server / cet-tutor-core / cet-tutor-server test 绿；默认 stub、mcp 默认关闭
- **下一步：** MVP-2B2 Web 录音/播放

## 会话：2026-09-09（续 6）MVP-2A MCP 脚手架

### MVP-2A：MCP 脚手架 + 供应商调研
- **状态：** complete
- 执行的操作：
  - 确认 `ApiKeyCipherServiceTest`（**kidora-agent-core**）4 tests 通过；manage 仅 AdminJwt/Builtin
  - findings / mcp-design：默认供应商 **Azure Speech F0**（ASR+TTS+发音）
  - 新建 `kidora-mcp-server`：`echo_ping` + ASR/TTS/发音 stub；Bearer 可选鉴权；12 tests
  - Flyway `V6__mcp_registry.sql` + schema `17/18` + CET 本地 seed
  - docs：architecture / AGENTS / README / database-design / task_plan
- 验收：`kidora-mcp-server` test+package；启动 :8081 日志 `Registered tools: 4`

## 会话：2026-09-09（续 5）提示词中文化

### 提示词种子与约束
- **状态：** complete
- 执行的操作：
  - **Flyway 规范：** 已落地的 `V2`/`V3`/`V4` **还原为原 checksum**；中文内容仅由 `V5__prompt_content_zh.sql` 写入（禁止改已应用脚本）
  - 约束：`AGENTS.md` / `docs/coding-standard.md` / `docs/database-design.md`；旁路 manage 同步
  - schema 列注释标明 `name`/`content` 须中文（`schema/*.sql` + V5 COMMENT）
- 验收：重启 `kidora-agent-server` 跑通 Flyway V5；管理台 Prompt 列表可见中文正文

## 会话：2026-09-09（续 4）管理台联调补洞

### 管理台补洞 + 联调
- **状态：** complete
- 执行的操作：
  - admin-web：401 `clearSession` 同步 Pinia；路由首访 `loadMe`
  - Prompt：`promptGroup` 前后端可写；CET 分组新建可见
  - `DevSeedRunner` 限 profile `local`/`dev`；默认 `spring.profiles.active=local`
  - 单测：AdminJwt 隔离、BuiltinAdminRules；ApiKeyCipher mask 单测在 **kidora-agent-core**（非 manage）
  - docs：路由别名、角色 MVP-1 落地说明、README seed 说明
- 验收：`kidora-agent-manage` test；`kidora-admin-web` build；手测 login→me→users/builtin→LLM 脱敏→CET Prompt 发布→401

## 会话：2026-09-09（续 3）收口 3c + 管理台 MVP-1

### 收口 3c
- **状态：** complete
- 执行的操作：
  - `CetLessonService`：`assertSessionOwned` / `softInputRedirectOrEmpty` + 单测
  - `postSse` 单次 `onDone`；CET plan 改 `localStorage`
- 验收：`cet-tutor-core` 19 tests；`kidora-web` build

### 管理台 MVP-1（旁路 kidora-ai-manage）
- **状态：** complete
- 执行的操作：
  - `kidora-agent-core`：`LlmConfigRepository` CRUD + `ApiKeyCipherService.mask`；`AdminAuthUser`
  - `kidora-agent-manage`：Admin JWT、users/llm/prompts/llm-calls、DevSeedRunner（admin/admin123）
  - `kidora-admin-web`：login / dashboard / users / llm / prompts / llm-calls
  - 双仓 docs / README / task_plan / findings 同步
- 验收：`kidora-agent-manage` package；`kidora-admin-web` build

## 会话：2026-09-09（续 2）阶段 3c

### 阶段 3c：CET 补洞 + 通用 Chat + kidora-web
- **状态：** complete
- 执行的操作：
  - Safety：L1 失败 / 非法 JSON → fail-closed `SOFT_BLOCK`；`policyVersion` 写入事件 detail
  - Tutor：先 `callText` 全文 → L2 → 分块 SSE；`Flux.defer` 离开 event-loop
  - `ChatFacade` + `chat_session`/`chat_message`；Flyway `V4__chat_prompt_seed.sql`
  - agent-server：`/api/chat/**`、`GET /api/learners`
  - 新建 `kidora-web`（登录 / 首页 / CET / report / chat）
  - 同步 architecture / agent-flow / safety / ui / README / task_plan / findings
- 验收：`cet-tutor-core` 单测通过；`kidora-agent-server` compile；`kidora-web` `npm run build` 通过

## 会话：2026-09-09（续）阶段 3b

### 阶段 3b：MVP-1 CET 文本陪练 API
- **状态：** complete
- 执行的操作：共享 jar + Auth + CET 开课/SSE/结课 + 文档
- 验收：login / open session / 单测

## 会话：2026-09-09

### 阶段 3a：MVP-1 数据库与最小脚手架
- **状态：** complete

## 会话：2026-09-08

### 阶段 1–2：需求与 MVP-0 文档
- **状态：** complete

## 测试结果

| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| Safety fail-closed | 非法 JSON / LLM throw | SOFT_BLOCK | OK | pass |
| Output gate | HARD 原文 | 替换句，无泄漏 | OK | pass |
| cet-tutor-core test | mvn test | SUCCESS | SUCCESS | pass |
| agent-server compile | mvn compile | SUCCESS | SUCCESS | pass |
| kidora-admin-web build | npm run build | SUCCESS | SUCCESS | pass |
| kidora-agent-manage package | mvn package | SUCCESS | SUCCESS | pass |
| manage 联调补洞 test | mvn test | SUCCESS | SUCCESS | pass |
| manage hand QA | login→prompt CET | OK | OK | pass |
| ApiKeyCipher mask (core) | mvn test | 4 pass | 4 pass | pass |
| kidora-mcp-server test | mvn test | 12 pass | 12 pass | pass |
| kidora-mcp-server boot | :8081 | Registered tools: 4 | OK | pass |

## 错误日志

| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-09-09 | Maven「No compiler… JRE」 | 1 | `JAVA_HOME` 指向 JDK 17 |
| 2026-09-09 | Mockito + JBR 25 mock JdbcTemplate | 1 | 改为静态归属断言单测 |
| 2026-09-09 | cet-server repackage rename lock | 1 | 进程占用 jar |
| 2026-09-09 | MCP bean 名冲突 connectivityTools | 1 | Provider bean 改名 `*ToolProvider` |

## 五问重启检查

| 问题 | 答案 |
|------|------|
| 我在哪里？ | MVP-2A（MCP stub + Azure 选型）已完成 |
| 我要去哪里？ | MVP-2B：Azure 接线 + CET Client + Web 语音 |
| 目标是什么？ | 儿童口语语音闭环（ASR/TTS/发音） |
| 我学到了什么？ | 见 findings.md（Azure F0 一家三工具） |
| 我做了什么？ | 见上方 2026-09-09（续 6） |

---
*每个阶段完成后或遇到错误时更新此文件*
