# CET 总设计：Child English Tutor Agent

架构名称：**Plan → Practice → Evaluate → Re-plan**。  
吸收 `tmp/儿童陪练规划1.md`、`tmp/Plan-and-Execute模式的设计.md`；工程模块见 [architecture.md](architecture.md)。

---

## 1. 背景与目标

孩子选定话题（如「介绍我的宠物」），系统扮演 AI 外教陪练，并给出发音、语法、词汇方面的鼓励式反馈；支持多人设；服务儿童安全与可追踪学习闭环。

**不是**「LLM + 一个聊天窗口」；要展示多 Agent、Memory、MCP、Safety、（后期）HITL。

---

## 2. 为什么不是纯 Plan-and-Execute

| 做法 | 问题 |
|---|---|
| 每轮说话都完整 Planner | 延迟差，口语体验崩 |
| 无计划纯闲聊 | 无法螺旋教学目标、无法 Re-plan |

结论：

- **大循环**：Plan-and-Execute（开课计划 → 阶段执行 → 评测 → 必要时再规划 → 结课）
- **小循环**：Conversational Tutor（外教问 ↔ 孩子答 ↔ 轻量分析 ↔ 反馈 ↔ 下一问）

硬性约束见 [agents.md](../agents.md) §4.3。

---

## 3. 逻辑组件

```
                    ┌──────────────┐
                    │ Safety Guard │
                    └──────┬───────┘
                           │
孩子 ──→ 输入 ─────────────┤
                           ▼
                    ┌──────────────┐
                    │   Planner    │  制定/修订 TrainingPlan
                    └──────┬───────┘
                           ▼
                    ┌──────────────┐
                    │ Tutor Loop   │  实时陪练（可调 ASR/TTS Tool）
                    └──────┬───────┘
                           ▼
                    ┌──────────────┐
                    │  Evaluator   │
                    └──────┬───────┘
                     ┌─────┴─────┐
                     ▼           ▼
               Re-Planner    Session 总结 → Memory
```

| 组件 | 实现归属 | 是否每轮 LLM |
|---|---|---|
| Safety Guard | `cet-tutor-core` | 规则 + 可选模型；可 MCP 补充 |
| Planner | `cet-tutor-core` | 是（开课/再规划） |
| Tutor | `cet-tutor-core` | 是（对话轮） |
| Tool 执行 | `kidora-mcp-server` | 否（工具） |
| Evaluator | `cet-tutor-core` | 阶段边界是；轮内可轻量 |
| Re-Planner | `cet-tutor-core` | 仅 Evaluator 触发 |
| Memory | `kidora-memory` | 结课/异步 |

对照经典 8 节点：Task Analyzer 可与 Planner 合并；Plan Validator 首期规则校验；Final Answer ≈ 结课报告。

---

## 4. 大循环 vs 小循环

### 4.1 大循环（会话级）

1. 主题 + learner + persona → Safety
2. Planner 生成 `TrainingPlan`
3. 按 stage 进入 Practice
4. 阶段结束 Evaluator
5. 未达标 → Re-Planner 改计划 → 回 Practice
6. 达标 → 报告 + Memory

### 4.2 小循环（轮次级）

0. **开场**：进入 PRACTICING 后客户端 `opening=true` → 外教开场（见下）+ TTS 可选；`child_text` 为空；**不**调用 Planner  
1. 孩子回答（文本或 ASR）  
2. 轻量分析（语法/关键词命中；发音 Tool 在 MVP-2）  
3. 鼓励式反馈 + 下一问（见纠错原则）  
4. **不**调用完整 Planner  

**开场话术（A0/A1，`cet.tutor.opening.*`；服务端注入 `personaName` + `Asia/Shanghai` 的 `dayGreeting`）：**

- 语气极度热情、友爱、口语短句（不堆感叹号）。  
- 固定三段、只提一个问题：① 英文问候 + 自我介绍（`I'm {{personaName}}` + `{{dayGreeting}}`）→ ② 中文点题（主题英文可放括号）→ ③ **英文下一问 + 括号中文**。  
- A2+ 开场仍偏英文短句 + 一问，不强制中文点题段。  

**纠错话术（Prompt 约束，非独立 Agent；适用于练习轮）：**

- 默认**隐式纠错**：正确英语复述 + 追问，不考试腔、不列分数。  
- 目标语法反复错或严重影响理解时**显式纠错**：短鼓励 → 1～3 句中文讲解 → 英文关键点/正确句 → 请孩子再说一次。  
- **中文脚手架**（A0/A1 默认；孩子中文求助；显式纠错）：鼓励/讲解/引导/追问用中文，示范句与关键点用英文；**练习轮** A0/A1 下一问默认中文，A2+ 平常仍以英文为主（**开场**下一问见上，用英文+括号中文）。  
- **气泡**可在英文后保留 `(中文注释)`；**禁止**整段「英文主句 + 逐句括号翻译」堆叠。  
- **TTS**（`speakableForTts`）：朗读中文正文 + 英文例句，去掉含中文的括号注释。发音评测仍用独立英文 `referenceText`，不把整段中文讲解当跟读稿。

---

## 5. 动态 Re-Plan 示例

原计划：学 pet/cute、`I have...`、5 轮对话。  
孩子出现 `He have big eyes.` → Evaluator 标语法弱项 → Re-Planner：暂停新词 → have/has 微练习 2 句 → 回到宠物主题。

这是 Agent 价值点；必须在 [cet-lesson-flow.md](cet-lesson-flow.md) 状态机中显式建模。

---

## 6. 与 Spring AI Alibaba

- Planner / Tutor / Evaluator：Agent 或结构化 ChatClient + 输出 Schema。
- 编排：Workflow / 状态机在 `cet-tutor-core`；禁止在 Controller 堆业务。
- 工具：MCP；禁止在 core 内写死供应商 SDK 扩散（防腐层可放 mcp-server）。

---

## 7. API 与模块

- Boot：`cet-tutor-server` → `/api/cet/**`
- Core：`cet-tutor-core`
- 人设：[cet-persona-design.md](cet-persona-design.md)
- 安全：[cet-safety-design.md](cet-safety-design.md)
- 评测：[cet-assessment-design.md](cet-assessment-design.md)

---

## 8. MVP 映射

| 能力 | MVP |
|---|---|
| Safety + Planner + Tutor 文本小循环 + 会话摘要 | 1 |
| ASR/TTS/发音 Tool | 2 |
| Re-Planner + 长期 Profile | 3 |
| 家长报告 + HITL | 4 |
| 课程 RAG 辅助策划 | 5 |

---

## 9. 验收（产品级）

1. 同会话内连续多轮对话延迟可接受（无每轮全量规划）。
2. 计划 JSON 可追踪修订历史（**已落地** `cet_training_plan_revision`）。
3. Safety 拦截可审计。
4. 结课报告对儿童鼓励、对家长可懂（MVP-4 完整）。
