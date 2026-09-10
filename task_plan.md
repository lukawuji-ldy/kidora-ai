# 任务计划：Kidora / CET 设计文档与平台启动

## 目标

完成双仓库设计文档后，落地 **MVP-1** 文本陪练，并完成 **阶段 3c**：CET 补洞 + 通用 Chat + `kidora-web`。

## 当前阶段

阶段 3c / 3d **complete**；**MVP-2A** / **MVP-2B1** / **MVP-2C** / **MVP-2B2** **complete**；**词典 MCP** **complete**；**MVP-3**（Re-plan + Profile）**complete**；下一阶段：MVP-4 或韧性扩展

## 各阶段

### 阶段 1：需求与发现
- [x] 理解用户意图（儿童英语口语陪练为首期）
- [x] 确定约束（双仓库、命名、CET 独立 Boot、全景+MVP）
- [x] 将发现记录到 findings.md
- **状态：** complete

### 阶段 2：设计文档交付（MVP-0）
- [x] 前台 agents + 平台 docs
- [x] 前台 CET 域 docs
- [x] 前台 README.md
- [x] 后台 agents + docs + README.md
- [x] 双仓库交叉核对
- **状态：** complete

### 阶段 3a：MVP-1 数据库与最小脚手架
- [x] 表空间 `ts_kidora` + 库 `kidora_ai`
- [x] `schema/` DDL + Flyway `V1__init.sql`
- [x] 最小可编译 `kidora-agent-server`
- [x] 同步 `docs/database-design.md` 与计划文档
- **状态：** complete

### 阶段 3b：MVP-1 CET 文本陪练 API
- [x] `kidora-common` + 薄 `kidora-memory`
- [x] `kidora-agent-core`（LLM/Prompt/审计/ModelRouter）
- [x] `kidora-agent-server`：JWT login + Flyway V2 seed
- [x] `cet-tutor-core`：状态机 / Safety / Planner / Tutor / Eval
- [x] `cet-tutor-server`：开课 / stream SSE / complete / report
- [x] 文档同步
- **状态：** complete

### 阶段 3c：CET 补洞 + 通用 Chat + kidora-web
- [x] Safety fail-closed + 输出先检后 SSE + stream defer + 单测
- [x] `ChatFacade` + Flyway V4 CHAT prompt + `/api/chat/**` + `/api/learners`
- [x] `kidora-web`：登录 / 首页 / CET 文本陪练 / 报告 / 最小 Chat
- [x] 收口：归属/SOFT 输入单测；SSE 单次 onDone；plan localStorage
- [x] 文档同步
- **状态：** complete

### 阶段 3d：管理台 MVP-1（旁路 kidora-ai-manage）
- [x] `kidora-agent-manage`：Admin JWT + users/llm/prompts/llm-calls
- [x] `kidora-admin-web`：登录与配置页
- [x] 双仓文档同步
- [x] 联调补洞：401/Pinia、prompt_group、DevSeed profile、最小单测
- **状态：** complete

### 阶段 4：MVP-2（语音 MCP）
- [x] **MVP-2A**：`kidora-mcp-server` 脚手架 + stub + `mcp_*` + Azure F0 选型
- [x] **MVP-2B1**：Azure REST + CET MCP Client + stream SSE
- [x] **MVP-2C**：讯飞/腾讯整栈 + `speech_vendor_config`/`speech_route` + 管理台主备（无自动 failover）；Azure 第三档暂不用
- [x] **MVP-2B2**：kidora-web 录音/播放闭环
- [x] 词典 `dictionary_lookup`（stub|http）
- **状态：** complete

### 阶段 5：MVP-3（Re-plan + 长期画像）
- [x] Evaluator `decision` + `LessonReplanner` + revision 表
- [x] 阶段达 `targetTurns` 触发 EVALUATING→REPLANNING→PRACTICING
- [x] `learner_profile.extra_json` + `learner_semantic_memory` 结课写入；Planner 注入
- [x] Flyway V8 + docs
- **状态：** complete

### 阶段 6：MVP-4+（后续）
- [ ] 家长报告、HITL、管理台 CET 运营页、RAG
- **状态：** pending

## 开放问题

1. ~~生产 ASR/TTS/发音供应商最终选型（MVP-2 前关闭）。~~ → **已关闭默认：Azure Speech F0**（接线前复核额度/区域）。
2. ~~儿童账号与家长账号的精确鉴权模型~~ → **已关闭：家长 JWT + learnerId 归属校验**。

## 已做决策

| 决策 | 理由 |
|------|------|
| 文档全景 + MVP 分期 | 避免只写 MVP 导致后续架构漂移 |
| A+B 双仓库文档同期 | 与参考仓拆分一致 |
| 通用 `kidora-*` / 域 `cet-*` | 多产品线扩展 |
| CET 独立 Boot | 语音/评测生命周期隔离 |
| 技术栈对齐 wuji | 降低学习与集成成本 |
| 库名 `kidora_ai` + 表空间 `ts_kidora` | 与参考仓同实例隔离数据；物理落盘独立 |
| 表结构仅由 Flyway 创建 | bootstrap 只建库/表空间；`all.sql` 互斥备用 |
| 家长 JWT + learnerId | JWT 不含儿童身份；归属由服务端校验 |
| CET MVP-1 用 ChatClient+状态机 | 无 MCP 工具时延迟可控；小循环禁止 Planner |
| 开课同步 JSON | 降低双通道复杂度；开课 SSE 后续可加 |
| Safety L1 失败 fail-closed | 儿童场景不可模型宕机放行 |
| Tutor 输出先检后发 | 避免 SSE 先泄漏再拦截 |
| Chat 无工具 ChatFacade | 本切片不做 ReactAgent/MCP |
| Web JWT localStorage | 双后端 rewrite 下实现简单 |
| Admin JWT 隔离 | 运营台与家长 JWT 双轨，防串用 |
| manage Flyway 关闭 | schema 权威在运行时仓，避免双源迁移 |
| MVP-2 默认供应商 Azure Speech F0 | （历史）一家覆盖 ASR+TTS+发音 |
| MVP-2C 主备讯飞/腾讯，无自动 failover | 管理台可选；Azure 第三档暂不用；运行只用 primary |
| MVP-2B2 Web 采 16k WAV | 避免 MediaRecorder webm/m4a；对齐讯飞 raw PCM |
| 本仓库只在当前分支（master）提交 | 禁止功能分支 / rebase / PR 分支流（见 coding-standard §7） |
| MVP-3 语义记忆无强制 embedding | 避免 pgvector 扩展阻断；检索后续 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| 默认 PATH 无 JDK（仅 JRE 8） | 1 | 构建使用 `~/.jdks/ms-17` 或 IntelliJ JBR |
| JBR 25 + Mockito 无法 mock JdbcTemplate | 1 | 归属校验改为静态 `assertOwned` 单测 |
| cet-tutor-server repackage 文件锁 | 1 | 关闭占用 jar 的进程后重试 |

## MVP 分期（跟踪）

| 阶段 | 范围 | 明确不做 | 状态 |
|---|---|---|---|
| MVP-0 | 双仓库 agents + docs + 计划文件 | 任何业务编码 | complete |
| MVP-1 DB | 库/表空间/DDL/最小 agent-server | CET 业务逻辑 | complete |
| MVP-1 | Safety + Planner + Tutor 文本小循环 + 会话评测摘要 | 每轮 Re-plan；完整 ASR/发音 MCP | complete |
| 3c | CET 补洞 + Chat + kidora-web | MCP 语音 | complete |
| 3d | 管理台 MVP-1（Admin API + Vue） | MCP/KB/HITL | complete |
| MVP-2A | `kidora-mcp-server` stub + `mcp_*` + Azure 选型 | 真供应商 / CET Client / Web 语音 | complete |
| MVP-2B1 | Azure REST + CET MCP Client + stream SSE | Web 录音/播放 | complete |
| MVP-2C | 讯飞/腾讯整栈 + 管理台主备 | Azure 暂不用 | complete |
| MVP-2B2 | kidora-web 录音/播放闭环 | 课程 RAG 生产化 | complete |
| MVP-2 | MCP：ASR / TTS / 发音评测；语音闭环 | 课程 RAG 生产化 | complete |
| 词典 | `dictionary_lookup` stub\|http | Tutor 工具环强制查词 | complete |
| MVP-3 | Evaluator 驱动 Re-Planner；长期 Learner Profile | 完整家长 HITL 产品 | complete |
| MVP-4 | 家长报告 + HITL；管理台 CET 运营页 | — | pending |
| MVP-5 | `kidora-rag`；更多 Kidora 产品线挂载 | — | pending |

## 备注

- 双仓库进度统一在本文件跟踪；管理台不另建完整 task_plan（可选轻量说明见 manage README）。
- 做重大决策前重读本文件与 [findings.md](findings.md)。
- Git：在 `master` 直接提交；禁止新建功能分支（见 [docs/coding-standard.md](docs/coding-standard.md) §7）。
