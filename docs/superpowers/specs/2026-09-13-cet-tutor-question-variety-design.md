# CET 陪练提问多样化设计

- **日期：** 2026-09-13
- **仓库：** `kidora-ai`
- **状态：** Implemented
- **关联：** `cet.tutor.system` / `cet.tutor.user` / `cet.tutor.opening.system` / `cet.planner.system` / `cet.replan.system`（V18）；英主脚手架见 [2026-09-11-cet-en-primary-scaffold-design.md](./2026-09-11-cet-en-primary-scaffold-design.md)

## 1. 背景与目标

Tutor 每轮即兴生成下一问。V17 把「Do you…? / Yes, I do」写成目标气泡范例，且 `planSummary` 过薄，现场易出现同模板连环（喜欢狗→猫→兔 + 什么颜色）与过多 yes/no。

**目标：**

1. 减少同模板换名词连环问。
2. yes/no（含 `Yes, I do` / `No, I don't` scaffold）可偶发，禁止连续两轮都是纯封闭问。
3. 颜色/大小等主题用指认、选择、对比、描述/跟读、个人 WH 轮换巩固。
4. **不**引入计划 JSON 新字段 `practiceMoves`；**不**改 Safety / stage 推进启发式。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 方案深度 | Prompt 优化 + 轻量上下文注入 |
| 英主脚手架（V17） | 保留：鼓励英文；每轮最多 1 处括号中文 |
| 结构化 practiceMoves | 不做 |
| 实现 | Flyway V18 增版 Prompt + `TutorLoop` 注入 `stageGoal` / `recentAsks` + Planner fallback |

### 1.2 非目标

- 不做题库或题型枚举落库
- 不改 `turnCount/2` stage 推进
- 不改前端 props / 主题包

## 2. 话术规则（练习轮增量）

在 V17 语言硬约束之上增加：

1. **反重复：** 下一问不得与 `{{recentAsks}}` 同模板仅换名词（禁止 Do you like X / What color is your X 对 dog→cat→rabbit 连环）。
2. **yes/no 配额：** 连续两轮不得都是可只用 yes/no 作答的封闭问；优先 WH / 二选一 / 对比 / 填空跟读。
3. **题型池（轮换）：** 指认、选择、对比、描述/跟读、个人 WH；yes/no 仅偶发。
4. **示范答句按题型：** `It's red.` / `A big dog.` / `I like blue.`；yes/no scaffold 仅当本轮确为 yes/no 问。
5. **目标气泡范例：** 至少 2 个非 yes/no 形态，替换 V17 单一 Do-you 范例。

开场：保留 A0/A1 三段式；下一问避免默认锁死 `Do you have…?`，可用 name/choose/WH。

## 3. 上下文注入

`TutorLoop` 向 Prompt 注入：

| 变量 | 来源 |
|---|---|
| `planSummary` | topic + objectives + vocabulary/patterns + childGoals 摘要 |
| `stageGoal` | 当前 `stageId` 对应 stage.goal |
| `recentAsks` | 近期轮次 tutor 文本中抽取出的问句（与 recentTurns 同窗口） |

## 4. 实现

- `V18__cet_tutor_question_variety.sql`：增版上述 Prompt（`change_note = V18 question variety`，幂等）
- `TutorLoop`：解析 stageGoal / recentAsks；扩展 summarizePlan
- `LessonPlanner.defaultPlanJson`：patterns 与 stage goal 多样化
- 同步 [docs/cet-tutor-design.md](../../cet-tutor-design.md)

## 5. 验收

- 新课宠物/颜色主题：连续 3+ 轮不应出现「Do you like X? + What color is your X?」同模板连环。
- yes/no scaffold 不作为每轮默认形态。
- 英主 + 每轮最多 1 处括号中文仍成立。
- Flyway 二次执行因 `change_note` 幂等跳过。
