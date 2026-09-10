# Agent 记忆与学习者画像设计

通用短/长期记忆 + CET Learner Profile。实现细节可对照参考仓记忆模式，字段与边界以本文为准。

---

## 1. 总览

| 层级 | 内容 | 生命周期 |
|---|---|---|
| Session Context | 当前陪练/聊天轮次原文与中间状态 | 会话结束可归档 |
| Short Memory | 入模窗口内摘要/最近 N 轮 | 会话级 |
| Learner Profile | CEFR、错因、词汇、偏好人设 | 长期 |
| Semantic Memory | 可检索的学习事实片段 | 长期 + 衰减 |

**原则：** 禁止把全部聊天原样写入长期记忆；以 Memory Action（INSERT/UPDATE/MERGE/DELETE/IGNORE）+ 冲突解决落库。

---

## 2. 标识约定

| 标识 | 含义 |
|---|---|
| `user_id` | 登录账号（多为家长） |
| `learner_id` | 儿童学习者 |
| `session_id` | 通用 chat 或 `cet_lesson_session.id`（类型字段区分） |

CET 路径必须以 `learner_id` 为画像主键；不可仅用家长 `user_id` 覆盖多孩。

---

## 3. 短期记忆

- 来源：`cet_tutor_turn` / `chat_message`。
- 入模：滑动窗口 + 可选阶段摘要；超限先摘要再截断。
- Tutor 小循环只读当前计划 + 短记忆，不加载全部历史会话。

---

## 4. 长期：Learner Profile

建议结构（逻辑模型，表见 [database-design.md](database-design.md)）：

```json
{
  "learnerId": "...",
  "vocabularyLevel": "A1",
  "commonGrammarErrors": ["he/she + have", "第三人称单数"],
  "pronunciationWeakness": ["/r/", "/θ/"],
  "knownVocabulary": ["dog", "cat", "cute"],
  "learningPreferences": ["喜欢故事", "喜欢动物"],
  "preferredPersonaIds": ["lily", "coco"]
}
```

| 字段组 | 写入时机 | MVP |
|---|---|---|
| CEFR / 年龄段 | 建档 + 定期重估 | 1 基础 / 3 自动 |
| 语法错 / 发音弱点 | Evaluator / 结课 | 3（`extra_json` 已写） |
| 已知词汇 | 结课合并 | 3（已写） |
| 偏好 | 家长设置或行为推断 | 1 / 3 |

---

## 5. Semantic Memory

- 向量检索「与主题相关的过往学习事实」。
- 与课程 RAG 分表；CET Planner 可同时读 Profile + 少量 semantic hits。
- **MVP-3 已启用**：表 `learner_semantic_memory`（本切片**不强制** pgvector embedding；向量检索后续）。

---

## 6. Memory Action 与抽取

- 默认异步抽取；显式「记住」可同步。
- 失败可降级规则启发式；模式可配 `hybrid` / `llm` / `rule`。
- CET 结课抽取优先更新 `learner_profile`，避免噪声写入通用 `user_profile`。

---

## 7. 读路径

```
请求
  → MemoryRoute（规则或 LLM 路由）
  → 加载 ACTIVE profile 字段子集
  → 可选 semantic 召回
  → 注入 System / Planner 上下文
```

注入内容须经 Safety 与长度预算（token cap）。

---

## 8. 生命周期

- 衰减 / 过期整理：后台任务（管理台可观测）。
- 家长可请求删除学习者数据（合规；MVP-4+ 产品化）。

---

## 9. 接口契约（目标）

| Port | 职责 |
|---|---|
| `LearnerProfilePort` | 读写画像 |
| `MemoryWritePort` | Action 落库 |
| `LongTermMemoryRetriever` | 读入模 |

---

## 10. 验收要点

1. 多孩家庭画像不串号。
2. 长对话不会无限入模。
3. 结课后面画像变更可解释（来源 session/turn）。
4. 日志无儿童原文全文。
