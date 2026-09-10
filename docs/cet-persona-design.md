# CET AI 外教人设（Persona）

通过不同导师个性提升儿童学习趣味性。选型进入 Planner 上下文与 Tutor 系统提示（配置化 Prompt，禁止代码硬编码长文）。

---

## 1. 人设一览

| ID | 名称 | 特点 | 适合 |
|---|---|---|---|
| `emma` | Emma | 温柔鼓励型 | 低龄、怯场 |
| `mike` | Mike | 探险游戏型 | 好动、喜欢任务 |
| `lily` | Lily | 故事型 | 喜欢叙事、动物/奇幻 |
| `tom` | Tom | 挑战型 | 需要一点难度刺激 |
| `coco` | Coco | 萌宠型 | 低龄、宠物主题 |
| `alex` | Alex | 游戏任务型 | 积分/关卡动机 |

（展示名可本地化；**稳定 id** 用于库与 API。）

---

## 2. 选型规则

1. 用户显式选择 > Profile `preferredPersonaIds` > 主题启发（如宠物→`coco`/`lily`）> 默认 `emma`。
2. Safety 与教学边界**高于**人设；任何 persona 不得越界。
3. Re-plan 默认**不更换**人设，除非家长设置或孩子明确要求。

---

## 3. Prompt 配置约定

- `prompt_group = CET`
- 代码键示例：`cet.persona.emma.system`、`cet.tutor.user`、`cet.planner.system`
- 人设差异：语气、比喻、任务包装；**共享**教学目标与 Safety 附录。

---

## 4. UI

- `kidora-web` 人设卡：头像、一句话特点、年龄建议标签。
- 陪练页轻量展示当前人设，不遮挡对话主区域。

详见 [ui-design.md](ui-design.md)。

---

## 5. 数据

- `cet_lesson_session.persona_id`
- Profile 可累计偏好计数（MVP-3）

---

## 6. MVP

| 能力 | MVP |
|---|---|
| 6 人设配置 + 手动选择 | 1 |
| 基于 Profile 推荐 | 3 |
| 更多人设运营可配 | 4（管理台） |
