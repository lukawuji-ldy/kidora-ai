# CET 上课记录单条/批量硬删除设计

- **日期：** 2026-09-10
- **仓库：** `kidora-ai`
- **状态：** Accepted（已实现于当前工作区；未要求 git commit）
- **入口：** `kidora-web` CET 首页「上课记录」列表

## 1. 背景与目标

CET 首页已展示学习者的上课记录（继续练习 / 回看 / 报告），但无法清理试课、中断或无记录。表 `cet_lesson_session` 虽有 `deleted` 软删字段，产品确认本期采用**物理硬删除**，并支持单条与批量。

**目标：**

1. 列表支持单条删除与「管理」模式下的批量删除。
2. 后端按会话归属鉴权后，事务内级联清除会话子数据。
3. 「练习中」删除需二次确认；批量删除一律确认。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 删除语义 | 硬删除（物理 DELETE），不可恢复 |
| 可删状态 | 任意状态均可删 |
| 单条确认 | 非练习中直接删；`PRACTICING` 需 confirm 并提示会中断且不可恢复 |
| 批量确认 | 一律 confirm；含练习中时文案加强 |
| 批量 UI | 先点「管理」再出现勾选框与批量操作 |
| API | 单条 `DELETE` + 批量 `POST .../batch-delete`（均返回 `ApiResponse` + `deletedCount`） |
| 审计日志 | 不删 `llm_call_log` |

### 1.2 非目标

- 不做回收站、撤销、软删切换
- 回看页 / 报告页 / 练习页本期不加深链删除入口
- 不清理 `llm_call_log`（审计保留，业务引用键可失效）
- 不做分页选择跨页全选等复杂列表能力

## 2. 数据语义

删除 `cet_lesson_session` 一行时，**同一事务**按 `lesson_session_id` 级联删除：

| 表 | 处理 |
|---|---|
| `cet_tutor_turn` | DELETE |
| `cet_turn_assessment` | DELETE |
| `cet_session_report` | DELETE |
| `cet_training_plan` | DELETE |
| `cet_training_plan_revision` | DELETE |
| `cet_safety_event` | 有关联该会话的行 DELETE |
| `cet_lesson_session` | DELETE（最后删主表） |
| `llm_call_log` | **保留** |

说明：现有查询多带 `deleted = FALSE`；硬删后行不存在，列表/详情自然不可见。本期不依赖、不回填软删标记作为删除手段。

## 3. API

基路径：`/api/cet`（`cet-tutor-server`）。身份以 User JWT 为准。

### 3.1 单条删除

```
DELETE /api/cet/sessions/{sessionId}
```

- 校验会话 `user_id` == JWT 用户
- 通过后硬删（级联如上）
- 成功：`200` + `{ "deletedCount": 1 }`（与现有 `ApiResponse` JSON 约定一致；不用裸 204，便于 Web `apiJson` 解析）
- 不存在或非本人：`404`（不区分「不存在」与「无权限」，避免枚举）

### 3.2 批量删除

```
POST /api/cet/sessions/batch-delete
Content-Type: application/json

{ "sessionIds": ["cls_...", "..."] }
```

- `sessionIds` 非空；先**去重**再校验；去重后上限 **50**；空列表或超限 → `400` + 中文错误信息
- 同一事务：先对每个 id 做归属校验，全部通过后再级联删除
- 任一 id 不存在或非本人 → 整批回滚，`404`，body 含可读中文错误（如「记录不存在或无权删除」）
- 成功：`200` + `{ "deletedCount": N }`（`N` 为实际删除条数，等于去重后的 id 数）

### 3.3 实现落点

| 层 | 职责 |
|---|---|
| `CetSessionController` | 暴露 DELETE / batch-delete；从 JWT 取 userId |
| `CetLessonService#deleteSessions(userId, ids)` | 归属校验 + `@Transactional` 级联删除 |
| `CetLessonSessionRepository`（及必要的 turn/plan/report repo 方法） | 按 sessionId（批量）DELETE 子表与主表 |

阻塞 JDBC 仍走现有有界线程池约定，不阻塞 WebFlux event-loop。

## 4. 前端交互（`kidora-web` `/cet`）

### 4.1 常态

- 每行 `history-actions` 增加「删除」ghost 按钮
- 「上课记录」标题旁增加「管理」按钮

### 4.2 单条删除

- 非 `PRACTICING`：调用单条 DELETE，成功后从本地 `sessions` 移除
- `PRACTICING`：`confirm`——「该课仍在练习中，删除后无法继续，且记录不可恢复。确定删除？」确认后再请求

### 4.3 管理模式

进入「管理」后：

- 每行左侧显示勾选框；**隐藏**行内「删除」（仅用批量，避免两套操作）
- 顶部工具条：已选 N 条、「全选」、「删除所选」、「完成」（退出并清空勾选）
- 「删除所选」在未勾选时禁用
- 批量一律 `confirm`：
  - 无练习中：`将永久删除 N 条上课记录，不可恢复。确定？`
  - 含练习中：在上句基础上追加「其中含练习中的课程，删除后无法继续。」
- 成功后从列表移除对应项并清空勾选，**保持管理态**（便于连续清理）；点「完成」退出

### 4.4 错误与防重

- 请求进行中禁用相关按钮，防重复提交
- 失败保留列表，展示后端消息或「删除失败，请重试」
- 已打开的回看/练习页不主动踢出；后续 API 404 走现有错误处理

## 5. 文档与测试

### 5.1 文档同步（实现交付时）

- `docs/cet-lesson-flow.md`：补充删除与级联约定
- 若 README / architecture 有 CET API 清单，同步两行删除接口

### 5.2 测试

**后端单元/集成（必须）：**

1. 非归属用户删除 → 404，数据未删
2. 单条删除成功 → 主表与子表无该 `lesson_session_id`
3. 批量中含无效 id → 整批回滚，有效 id 亦未删
4. 空列表 / 超过 50 → 400
5. `llm_call_log` 不被删除（若测试库有相关行）

**前端：** 手工验收管理进出、确认文案、列表更新即可（本期可不加强制 E2E）。

### 5.3 验收清单

1. 单条删非练习中：列表立即消失，无练习中专用确认
2. 单条删练习中：确认文案含中断 + 不可恢复
3. 管理批量：一律确认；含练习中文案加强
4. 已删 session 的回看/报告/继续练习不可用
5. 子表随会话清除；审计日志保留

## 6. 风险与说明

- 硬删除与表上已有 `deleted` 软删列并存：本期产品路径只用硬删；软删列可保留给未来或其它内部用途，但不作为本功能实现。
- 无 DB 级 FK CASCADE 时，必须在应用层按固定顺序删子表再删主表，避免孤儿行。
