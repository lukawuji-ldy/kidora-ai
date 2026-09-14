# CET 沉浸式大舞台：外教常大、亲切在场

- **日期：** 2026-09-11
- **仓库：** `kidora-ai`
- **状态：** Implemented
- **关联：** [ui-design.md](../../ui-design.md)、[2026-09-11-cet-persona-fixed-stage-design.md](./2026-09-11-cet-persona-fixed-stage-design.md)、[2026-09-11-cet-call-presence-ux-design.md](./2026-09-11-cet-call-presence-ux-design.md)、[2026-09-11-cet-persona-presence-polish-design.md](./2026-09-11-cet-persona-presence-polish-design.md)
- **升级说明：** 取代 fixed-stage「分区抢高度」实现；固定舞台目标（人像常在视口）保留，改为**叠层**以免人像缩成小头像。

## 1. 背景与目标

fixed-stage 用纵向 Flex 把人像 / 字幕 / 底栏分栏后，字幕与录音区吃掉垂直空间，半身像被压到约 1/5 屏，在场感变差。

**目标：**

1. 外教半身像在任意交互态下都**很大**（目标约占视口 **65–80%** 高度），像面对面视频通话。
2. 人像**始终在视口内**（不整页滚动顶走）。
3. 字幕与录音不「抢」人像高度：改为压在舞台下沿的毛玻璃浮层。
4. 历史用底部抽屉，打开时盖住下半区，背后人像仍大面积可见。

**非目标：** 全出血铺满背景（方案 2）、画中画、Live2D、后端/SSE/素材策略变更。

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 方案 | **沉浸式大舞台（方案 1）** |
| 人像 | 舞台内主视觉；`aspect-ratio 3/4`；宽可达约 `min(92%, 400px)` |
| 字幕 / 录音 | 绝对定位叠在 `call-stage` 底部；不参与 flex 高度竞争 |
| 历史 | 叠层内抽屉（字幕与录音之间，`max-height ≈ 36vh`），区内滚动；不盖住录音按钮 |

---

## 2. 布局契约

```
┌─────────────────────────────────────┐  100dvh, overflow:hidden
│ call-chrome（顶栏 + 一行 plan）       │  auto, z 高
├─────────────────────────────────────┤
│ call-stage（flex:1，相对定位）         │
│   PersonaStage ── 填满舞台            │
│   call-overlays（absolute bottom）    │
│     · call-captions（当前字幕）        │
│     · call-history-sheet（可选）       │
│     · call-dock（录音/打字）           │
└─────────────────────────────────────┘
```

---

## 3. 实现要点

- [`page.tsx`](../../../kidora-web/src/app/cet/session/[id]/page.tsx)：`call-stage` 包裹人像；字幕+dock 进 `call-overlays`；历史独立 `call-history-sheet`
- [`globals.css`](../../../kidora-web/src/app/globals.css)：immersive 叠层样式；放大 `.persona-avatar`；overlay 底部渐变保证字幕可读
- `PersonaStage` 状态机 / 视频逻辑不变

---

## 4. 验收

- 默认：半身像明显大于字幕区，约占上大半屏
- 录音 / 打字：人像尺寸基本不变（浮层变高叠在画面上）
- 展开历史：抽屉从底部升起，人像上部仍大面积可见
- 整页不滚动；`prefers-reduced-motion` 不变

---

## 6. 视频通话感精修（2026-09-11）

在沉浸叠层之上再收一刀：

| 项 | 做法 |
|---|---|
| 人像 | 更大半身卡（宽可达约 `460px`）；姓名/状态/主题芯片叠在画面上，减少「相框+文字条」分层 |
| 字幕 | 默认 **2 行** 字幕条；「全文」展开；少挡脸 |
| 录音 | 底部**悬浮胶囊**，去掉大白面板 |
| 课题说明 | 顶栏可折叠 `details`，默认收起一行入口 |

验收补充：默认态半身像视觉上应占舞台大半，字幕不再形成「第二张大卡片」压过人像。
