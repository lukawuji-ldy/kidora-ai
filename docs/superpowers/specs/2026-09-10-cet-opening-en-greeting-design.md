# CET 开场：英文问候 + 自我介绍 + 中文点题 + 英文提问

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`
- **状态：** Draft
- **关联：** `cet.tutor.opening.system` / `cet.tutor.opening.user`；`TutorLoop.generateOpening`；既有中文脚手架 spec（`2026-09-10-cet-tutor-zh-scaffold-tts-design.md`）

## 1. 背景与目标

当前 A0/A1 开场偏中文（例：`你好！今天我们来聊聊我的宠物猫 (my pet cat)。你有猫吗？`），缺少外教式英文问候与自我介绍，末问也未催促孩子用英语开口。

**目标：** A0/A1 开场改为固定三段结构，语气极度热情友爱；时段问候由服务端按北京时间注入；末问用英文并附括号中文。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 适用范围 | **仅 A0/A1**；A2+ 开场仍偏英文，不强制本三段式 |
| 时段问候 | **服务端注入** `dayGreeting`；时区 **`Asia/Shanghai`** |
| 末问形态 | **英文 + 括号中文**（例：`Do you have a cat?（你有猫吗？）`） |
| 语气 | **极度热情、友爱**；口语短句；像见到孩子很开心；不堆砌感叹号、不煽情过头 |
| 实现路径 | 改 Opening Prompt + `TutorLoop` 注入 `personaName` / `dayGreeting` |

### 1.2 非目标

- 不改练习轮 `cet.tutor.system` 主脚手架规则（鼓励/讲解/追问仍可中文；本变更仅开场例外）
- 不改 SSE 协议、Safety、Planner、Eval、TTS 剥括号逻辑
- 不做结构化开场 JSON；仍由 LLM 生成全文
- 不新增 DB 表；仅 Flyway 更新 Prompt（及 published version 同步）
- 不做前端改动

### 1.3 与既有脚手架的关系

| 场景 | 下一问语言 |
|---|---|
| **开场** A0/A1 | **英文 + 括号中文**（本 spec） |
| **练习轮** A0/A1 脚手架 | 默认仍中文追问；必要时可用英文问句 + 括号中文（既有规则） |
| A2+ 平常 | 以英文为主 |

文档须显式写清：「A0/A1 下一问默认中文」适用于**练习轮**，**开场**按本 spec。

## 2. 话术规则

### 2.1 A0/A1 开场三段（顺序固定，只提一个问题）

1. **英文问候 + 自我介绍**  
   使用注入变量，勿臆造人名或时段。  
   例：`Hi! I'm Emma. Good morning!`  
   语气：热情、温暖、兴奋见到孩子；可少量口语词（如 `so glad to see you`），但仍短。
2. **中文点题**  
   例：`你好！今天我们来聊聊我的宠物猫（my pet cat）。`
3. **英文下一问 + 括号中文**  
   例：`Do you have a cat?（你有猫吗？）`

### 2.2 约束

- 禁止长篇；禁止一次问多个问题；禁止不安全话题
- 气泡保留 `(中文)` / `（中文）`；TTS 仍剥含中文的括号注释（既有 `speakableForTts`）
- 自我介绍人名必须用 `{{personaName}}`；时段必须用 `{{dayGreeting}}`
- 人设差异：可在热情程度上微调（emma 更温柔、mike 更活泼等），但不得省略三段结构

### 2.3 目标气泡示例

> Hi! I'm Emma. Good morning! So happy to see you!  
> 你好！今天我们来聊聊我的宠物猫（my pet cat）。  
> Do you have a cat?（你有猫吗？）

### 2.4 A2+ 开场

保持既有「主句偏英文」方向；不强制中文点题段；不强制注入字段进结构约束（变量仍可传入，模板中 A2+ 分支不依赖三段式）。

## 3. 服务端注入

### 3.1 `dayGreeting`（`Asia/Shanghai`）

| 本地小时 `h` | `dayGreeting` |
|---|---|
| `0 <= h < 12` | `Good morning` |
| `12 <= h < 18` | `Good afternoon` |
| 其余 | `Good evening` |

### 3.2 `personaName`

由 `personaId` 映射展示名（首字母大写稳定名）：

| personaId | personaName |
|---|---|
| emma | Emma |
| mike | Mike |
| lily | Lily |
| tom | Tom |
| coco | Coco |
| alex | Alex |

未知 id：将 `personaId` 首字母大写后使用；空则 `Emma`。

### 3.3 代码落点

- `TutorLoop.generateOpening`：`vars` 增加 `personaName`、`dayGreeting`（及既有 `personaId` / `cefr` / `topic` / `planSummary`）
- 时段计算封装为小函数（便于单测）；时钟可注入或测静态映射表，避免 flaky

## 4. Prompt 变更

新 Flyway（建议 `V13__cet_opening_en_greeting.sql`，以仓库实际下一序号为准）：

- `UPDATE prompt_template`：`cet.tutor.opening.system`、`cet.tutor.opening.user`
- 同步 `prompt_template_version` 当前 published 行（与 V12 同模式）

**system 要点（中文正文）：**

- A0/A1：必须按 §2.1 三段；语气极度热情友爱
- 强制使用 `{{personaName}}`、`{{dayGreeting}}`
- A2+：短英文开场 + 一问；括号中文最多 1～2 处
- TTS 剥括号说明保留

**user 要点：**

- 传入主题/CEFR/人设/时段；明确「生成开场」任务

## 5. 文档与测试

| 项 | 要求 |
|---|---|
| `docs/cet-tutor-design.md` | 开场规则与练习轮脚手架分流；语气要求一句 |
| 本 spec | 实现后改状态为 Implemented |
| 单测 | `dayGreeting` 边界（11/12/17/18）；`personaName` 映射；可选：opening 调用 vars 含新键 |

## 6. 验收示例

给定：`cefr=A1`，`personaId=emma`，上海时间上午，`topic=My pet cat`。

期望开场语义包含：

1. `I'm Emma` 与 `Good morning`
2. 中文点题含主题，英文关键词可在括号
3. 英文问句 + 括号中文
4. 整体热情短句；仅一个问题
