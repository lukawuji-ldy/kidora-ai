# 课程知识库（RAG）设计

> **MVP 标签：目标全景 / 落地为 MVP-5。** MVP-1～4 不以 RAG 为陪练前置依赖。

为 Kidora 课程内容、例句、主题素材提供入库与检索；供通用 Chat Tool 与 CET Planner（可选）使用。

---

## 1. 目标与默认模式

- 默认检索后端：PGVector（`kb_chunk.embedding`）。
- 可选：Elasticsearch 8.15.4 Hybrid（与参考仓一致）；切换需重启并 rebuild，禁止静默双后端。

---

## 2. 与 Memory 边界

| | RAG | Memory |
|---|---|---|
| 内容 | 课程/教材/公共素材 | 学习者个人状态 |
| 写入 | 运营入库 | 会话抽取 / 家长设置 |
| 检索 | 主题/知识点 | 个人错因与偏好 |

禁止把 Learner Profile 当知识库 chunk 写入。

---

## 3. 检索约定

- Agent 侧经 Tool（如 `knowledge_retrieval`）或 Planner 专用 Port。
- 必须带引用；无法回答时拒答策略（实现期定义阈值）。
- CET 使用时优先检索「年龄/CEFR 适配」元数据过滤。

---

## 4. 版本 / 停用

- 文档版本 + Chunk Revision；ACTIVE 版本可检索。
- 停用文档立即不可检索，历史引用可保留快照（可选）。

---

## 5. 入库流水线

上传 → 预处理 → 中文/英文切分 → Embedding 批量（限速/429 退避）→ 激活版本。

管理台负责运营操作；运行时只读检索（除重建任务）。

---

## 6. 模块草图

- 库模块：`kidora-rag`
- 管理写路径：旁路 `kidora-agent-manage`
- 运行时读路径：`kidora-agent-server` / `cet-tutor-server`

---

## 7. 失败降级

- Embedding 失败：入库任务重试；检索失败：Tool 返回错误，禁止编造引用。
- 不与 Memory 向量表混用兜底。

---

## 8. 评测验收（MVP-5）

- 抽样问答命中率、引用正确率、拒答率。
- CET 主题素材检索延迟预算（实现期定）。

---

## 9. 可观测

- span：`rag.retrieve`、`rag.ingest`；记录 backend、topK、latency。
