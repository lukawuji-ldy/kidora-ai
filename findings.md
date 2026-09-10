# 发现与决策

## 需求

- 远景：儿童健康与成长 Agent 平台（Kidora）。
- 首期产品：儿童英语口语陪练 Agent（CET = Child English Tutor）。
- 双仓库：运行时 [`kidora-ai`](.)，运营管理台旁路 [`kidora-ai-manage`](../kidora-ai-manage)。
- MVP-0：只交付设计文档与项目计划文件。
- MVP-1 数据库切片（2026-09-09）：独立库/表空间 + DDL + 最小 `kidora-agent-server`。
- MVP-1 CET API（2026-09-09 续）：共享 jar + Auth + `cet-tutor-server` 文本闭环。
- 阶段 3c（2026-09-09 续 2）：CET Safety 补洞 + 通用 Chat + `kidora-web`。

## 研究发现

### 来自 tmp 调研

1. **Plan-and-Execute 适合「课程/训练大循环」，不适合「实时口语小循环」。**  
   每轮孩子说话后同步重规划会严重拖慢体验；Planner 应在开课/阶段间隙更新计划。
2. **推荐架构名：Plan → Practice → Evaluate → Re-plan。**  
   组成：Safety Guard + Lesson Planner + Conversational Tutor Loop + Evaluator + Re-Planner + Learner Memory + MCP/Tools +（后期）Human-in-the-loop。
3. **动态 Re-Plan 才是 Agent 价值点**：根据评测（语法/词汇/发音）暂停新内容、插入微练习，再回到主题。
4. **Safety 必须是独立系统约束**，不能只靠 Prompt「你是儿童老师」。
5. **长期记忆 / Learner Profile**：CEFR、常见语法错、发音弱点、已知词汇、偏好人设；供下次 Planner 使用。
6. **6 类 AI 外教人设**提升趣味性（Emma / Mike / Lily / Tom / Coco / Alex）。
7. 工程参考：经典 Plan-and-Execute 可拆 6～8 节点，但并非每节点都必须是独立 LLM Agent。

### 来自参考仓库

| 参考 | 路径 | 可复用 |
|---|---|---|
| 运行时总纲与 docs 结构 | `wuji-assistant-agent` | `agents.md` 七段结构、无 parent reactor、硬性约束、文档同步 |
| 管理台拆分 | `wj-assistant-agent-manage`（非 `wuji-*-manage`） | Admin 独立仓库、只暴露 `/api/admin/**`、依赖旁路 install 的 core |
| Plan-and-Execute 详设 | `tmp/Plan-and-Execute模式的设计.md` | 任务分析→计划→校验→执行→评测→再规划 |
| 次要 | `deepresearch` | 仅在无其它资料时参考 |

- MVP-0 文档已齐全；根 pom 仍为非聚合 stub。
- 已落地：`schema/`、`kidora-agent-server`（Flyway V1–V4）、`kidora-common` / `kidora-memory` / `kidora-agent-core`、`cet-tutor-core` / `cet-tutor-server`、`kidora-web`。
- PostgreSQL：同机实例 `127.0.0.1:5432`，库 **`kidora_ai`**，表空间 **`ts_kidora`**（异于 wuji 的 `vector_test`）。
- 演示账号：`parent1` / `parent123`；学习者 `lrn_demo_amy`。

## 技术决策

| 决策 | 理由 |
|------|------|
| 通用模块 `kidora-*`，CET 域 `cet-*` | 平台将扩展多产品线；英文陪练与通用能力解耦 |
| `groupId` = `com.wuji.kidora.ai` | 启动规划已锁定 |
| CET 独立 Boot：`cet-tutor-server` + `cet-tutor-core` | 陪练含语音/评测生命周期，与通用 Chat 分离（对齐参考仓 VTA 独立进程） |
| 大循环 Plan-and-Execute + 小循环 Tutor | 兼顾教学规划与实时性 |
| 双仓库：运行时 / 管理台 | 对齐 wuji 拆分，避免 Admin 与 Chat 同进程 |
| 技术栈版本对齐 wuji（Boot 3.4.8 / SAA 1.1.2.2 等） | 团队已有实践与 examples 对照 |
| 无仓库级 parent POM | 与参考仓一致，避免 reactor 耦合 |
| MVP-0 只文档 | 降低未对齐设计前的返工 |
| ASR/TTS/发音供应商本期不定死 | 在 MCP 设计中列候选与选型原则 |
| 库名 `kidora_ai` + 表空间 `ts_kidora` | 与 wuji 同实例隔离；物理目录独立 |
| 主键 BIGINT + 业务 VARCHAR 键 | 对齐 wuji DDL 风格 |
| 表仅由 Flyway 创建；`all.sql` 互斥备用 | 避免双路径重复建表 |
| 本切片不强制 `pgvector` | MVP-1 无向量表；避免扩展缺失阻断 |
| **家长 JWT + learnerId 归属** | JWT 不含儿童身份；开课/陪练校验 `learner_profile.user_id` |
| **CET ChatClient + 状态机** | MVP-1 无工具；小循环禁止 Planner；易单测 |
| **开课同步 JSON** | 降低开课 SSE 双通道复杂度 |
| **Safety fail-closed** | L1 宕机不得放行儿童内容 |
| **Tutor 输出先检后发** | 避免 SSE 先泄漏不安全 token |
| **ChatFacade 无工具** | 3c 不做 ReactAgent/MCP；短窗 20 条 |
| **Web JWT localStorage** | 双后端 rewrite 下实现简单 |
| **Admin JWT 隔离** | manage issuer=`kidora-admin`；禁止家长 JWT 进运营台 |
| **manage Flyway 关** | schema 权威在运行时仓；DevSeedRunner 写 builtin admin |
| **DevSeed 限 local/dev** | 避免非本地启动反复重置 admin123 |
| **401 清 Pinia** | 仅清 localStorage 会导致 login↔dashboard 死循环 |

## MVP 分期摘要

| 阶段 | 范围 |
|---|---|
| MVP-0 | 双仓库 agents + docs + 计划文件（完成） |
| MVP-1 DB | 库/表空间/DDL/最小 agent-server（完成） |
| MVP-1 | Safety + Planner + Tutor 文本小循环 + 会话评测摘要（完成） |
| 3c | CET 补洞 + Chat + kidora-web（完成，含收口） |
| 3d 管理台 MVP-1 | Admin API + Vue：账号/LLM/Prompt/调用日志（完成） |
| MVP-2 | MCP：ASR / TTS / 发音评测；语音闭环 |
| MVP-3 | Re-Planner + 长期 Learner Profile |
| MVP-4 | 家长报告 + HITL；管理台 CET 运营页 |
| MVP-5 | `kidora-rag` 课程库；更多产品线挂载 |

## 遇到的问题

| 问题 | 解决方案 |
|------|---------|
| 启动规划写的 `wuji-assistant-agent-manage` 不存在 | 实际参考 `wj-assistant-agent-manage` |
| 纯 Plan-and-Execute 做口语会卡 | 文档强制：禁止每轮同步 Re-plan |
| PATH 默认 Java 8 JRE，无法 `javac` | 构建用 IntelliJ JBR，`maven.compiler.release=17` |
| JBR 25 Mockito 无法 mock JdbcTemplate | 归属校验单测改为静态方法 |

## 资源

- `tmp/项目启动规划.md`
- `tmp/儿童陪练规划1.md` / `tmp/儿童陪练规划2.md`
- `tmp/Plan-and-Execute模式的设计.md`
- 旁路：`wuji-assistant-agent`、`wj-assistant-agent-manage`

## 免费语音能力候选（选型原则，非合同）

选型原则：儿童场景合规、延迟可接受、可 MCP 封装；**整栈按供应商族**（ASR+TTS+发音）。

### 当前默认 / 备选（管理台可切换，无自动 failover）

| 角色 | 供应商 | 能力 | 备注 |
|---|---|---|---|
| **默认 primary** | 讯飞开放平台 | 听写 + 合成 + **ISE** | seed：`speech_route.primary=iflytek` |
| **备选 backup** | 腾讯云 | ASR + TTS + **智聆 SOE** | 仅配置；运行时不自动切换 |
| **第三档 tertiary** | Azure Speech | ASR + TTS + Pronunciation | 代码保留，**暂不启用** |

运行：`kidora.speech.mode=db` 时 mcp-server 只调 primary；`mode=stub` 保本地 CI。

### 历史：Azure F0（曾为 MVP-2B 默认接线目标）

| 能力 | 方案 | 免费额度（公开价目） | 说明 |
|---|---|---|---|
| ASR | Azure STT | ~5 小时/月 | 第三档保留 |
| TTS | Azure Neural TTS | ~50 万字符/月 | 第三档保留 |
| 发音 | Pronunciation Assessment | 计入 STT | 第三档保留 |

### 其它备选

| 能力 | 候选 | 备注 |
|---|---|---|
| ASR | 自建 Whisper（MIT） | 零 API 费；自担 GPU/运维 |
| 原型 | 浏览器 Web Speech | 仅原型，不做生产 MCP |

**合规备注：** 音频短 TTL、日志不落密钥/完整 audioBase64；儿童数据协议按所选云厂商区域确认。

权威清单见 [docs/mcp-design.md](docs/mcp-design.md)。

---
*权威决策以 [agents.md](agents.md) 与 [task_plan.md](task_plan.md) 为准；本文件为调研沉淀。*
