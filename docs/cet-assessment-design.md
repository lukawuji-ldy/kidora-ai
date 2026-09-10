# CET 评测与家长报告设计

发音、语法、词汇、流利度评价及报告形态。流程触发见 [cet-lesson-flow.md](cet-lesson-flow.md)。

---

## 1. 评测维度

| 维度 | 说明 | MVP |
|---|---|---|
| grammar | 句型/时态/主谓一致等 | 1（LLM 结构化） |
| vocabulary | 目标词使用与适度拓展 | 1 |
| pronunciation | 音素/重音；依赖 Tool | 2（MVP-1 可用文本近似提示） |
| fluency | 完整度、犹豫、轮次完成 | 1（启发式 + LLM） |
| confidence / affect | 可选：鼓励策略输入 | 3+ |

输出必须含 **encouragement** 字段；禁止羞辱性表述（Safety 输出侧再检）。

---

## 2. 粒度

| 粒度 | 用途 | MVP |
|---|---|---|
| Turn 轻量 | 是否命中目标词、明显语法点 | 1 可选 |
| Stage / Session | 驱动 Re-plan 与报告 | 1 摘要 / 3 细粒度 |
| 纵向 | 写入 Learner Profile | 3 |

JSON 最小字段见 [database-design.md](database-design.md) §3.3。

---

## 3. Evaluator 职责

1. 读取：当前 plan objectives + 近 N 轮 turn。
2. 产出：结构化 scores + problems + 建议微练习。
3. 决策信号：`continue` | `replan` | `complete`。
4. **不**直接对儿童发长文报告（由报告模块渲染）。

---

## 4. 与 Re-Planner 接口

```json
{
  "decision": "replan",
  "focus": ["grammar:third_person_s"],
  "pauseNewVocab": true,
  "insertStage": { "id": "micro_drill", "goal": "have/has" }
}
```

Re-Planner 生成新 `cet_training_plan` 并写 `cet_training_plan_revision`（**已落地 MVP-3**）。

---

## 5. 儿童报告 vs 家长报告

| | 儿童 | 家长 |
|---|---|---|
| 语气 | 鼓励、徽章、简单星星 | 清晰分数与建议 |
| 内容 | 「你学会了…」「下次试试…」 | 错因、词汇表、练习建议、安全摘要 |
| MVP | 1 摘要 | 4 完整 |

同一 `cet_session_report` 可用 JSON 分 `childView` / `parentView`。

---

## 6. 发音评测（MVP-2）

- Tool：`pronunciation_score`（MCP）。
- 结果映射到 `pronunciation` 维度；失败则降级为「暂无发音分」不阻塞结课。

---

## 7. 可观测与审计

- `llm_call_log.caller = CET_EVAL`
- 报告生成可追溯 sessionId / planRevision

---

## 8. 验收

1. 故意语法错误能稳定出现在 `problems`。
2. 鼓励字段非空且通过 Safety。
3. `replan` 信号能被课时状态机消费（**已落地**：`EVALUATING→REPLANNING→PRACTICING`）。
4. 家长报告无儿童不可理解的原始模型 dump（MVP-4）。
