# 前端 UI 与交互设计（kidora-web）

`kidora-web`（Next.js）为 Kidora 统一前台壳；CET 为产品路由之一。  
管理 UI 见旁路 `kidora-admin-web`，不在本仓库。

---

## 1. 设计原则

1. 儿童主路径：少字、大按钮、强反馈、避免信息过载。
2. 家长路径：报告、设置、学习者档案；与儿童会话 UI 分区。
3. 可解释性：工具/思考面板在家长或调试模式可用；儿童模式默认隐藏复杂面板。
4. 品牌：Kidora；CET 产品名 Child English Tutor。

---

## 2. 工程划分

| 工程 | 技术 | 职责 |
|---|---|---|
| `kidora-web` | Next.js | 用户前台 |
| `kidora-admin-web` | Vue3（旁路） | 运营后台 |

开发态代理：

- `/api/*`（非 cet）→ `kidora-agent-server`
- `/api/cet/*` → `cet-tutor-server`

---

## 3. 信息架构

```
/login
/home                 # 产品入口（CET 卡片 + 未来其它）
/cet                  # 选主题 / 人设 / 学习者
/cet/session/[id]     # 陪练主界面
/cet/report/[id]      # 会话报告
/settings             # 账号与学习者档案
/chat                 # 通用助手（平台预留）
```

---

## 4. 登录

- User JWT；刷新策略实现期定。
- 儿童使用可走家长监护下的 learner 切换，不单独弱密码儿童账号（默认策略；合规细化 MVP-4）。

---

## 5. CET 陪练主界面（要点）

**首屏（儿童模式）只保留：**

- 当前外教人设与主题
- 对话气泡 / 语音按钮
- 鼓励式反馈条（简短）
- 结束练习

**不在首屏堆：** 复杂统计、完整计划树、工具原始 JSON。

家长/调试抽屉可看：当前计划目标、阶段、评测分数。

---

## 6. 消息与流式

- SSE 消费 [agent-flow.md](agent-flow.md) 事件。
- 儿童侧：优先展示 Tutor 话语与鼓励；`cet.assessment` 转为友好文案。
- `safety.block`：温和提示换主题/重说，不展示内部策略名。

---

## 7. 人设选择

- 展示 6 类人设卡片（见 [cet-persona-design.md](cet-persona-design.md)）。
- 可根据 Profile 推荐，允许家长覆盖。

---

## 8. 与后台边界

- 无 Admin API 调用。
- Prompt/LLM/MCP 配置不在用户前台暴露。

---

## 9. 感知与无障碍

- 大点击区；支持键盘；动画克制（降低眩晕）。
- 实现期指定前端模型：见 [agents.md](../agents.md) §5。

---

## 10. MVP

| 能力 | MVP | 状态 |
|---|---|---|
| 登录 + CET 文本陪练页 | 1 / 3c | ✓（`/login` `/home` `/cet` `/cet/session/[id]` `/cet/report/[id]`） |
| 通用 `/chat` 最小页 | 3c | ✓ |
| 语音按钮与播放 | 2B2 | ✓（16k WAV 录音 → `audioBase64`；消费 `audio.tts` / `pronunciation`） |
| 家长报告页产品化 | 4 | 未做（本期仅儿童摘要） |

**鉴权：** JWT 存 `localStorage`（`kidora_token`），请求 `Authorization: Bearer`。  
**代理：** `/api/auth|chat|learners` → `:8080`；`/api/cet` → `:8082`。  
**语音：** CET 会话页「录音」采集 16k 单声道 WAV（非 MediaRecorder webm/m4a）；发送 `{audioBase64, locale, referenceText?}`（`referenceText` 取上一句 Tutor）；儿童气泡显示「（语音）」；自动播放 `audio.tts`；有评分时展示四维分数。文本输入仍可用。