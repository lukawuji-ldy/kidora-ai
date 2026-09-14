# CET 固定舞台：人像常在视口

- **日期：** 2026-09-11
- **仓库：** `kidora-ai`
- **状态：** Superseded（2026-09-11）→ [沉浸式大舞台](./2026-09-11-cet-persona-immersive-stage-design.md)
- **关联：** [ui-design.md](../../ui-design.md)、[2026-09-11-cet-call-presence-ux-design.md](./2026-09-11-cet-call-presence-ux-design.md)、[2026-09-11-cet-persona-presence-polish-design.md](./2026-09-11-cet-persona-presence-polish-design.md)、[2026-09-11-cet-persona-immersive-stage-design.md](./2026-09-11-cet-persona-immersive-stage-design.md)

> **升级说明：** 分区 Flex 实现导致人像过小；「人像常在视口」目标由 [immersive-stage](./2026-09-11-cet-persona-immersive-stage-design.md) 以叠层大舞台承接。下文保留作决策记录。

## 1. 背景与目标

通话壳与真人半身像已落地，但 `/cet/session/[id]` 仍是**整页纵向文档流**：`PersonaStage`（约 `58vh`）→ 字幕/历史 → 底部录音区。展开历史或打开打字后总高度超过视口，页面滚动，半身像被顶出屏幕。

**目标：** 任意交互态（默认、录音、打字展开、历史展开）下，机器陪聊**半身像完整卡**始终留在视口上半区可见。

**非目标：**

- 不改后端 / SSE / Safety / Prompt
- 不改人设素材与 `speaking` 视频策略
- 不恢复大气泡墙
- 不做 Live2D / 全屏视频通话壳

### 1.1 已确认决策

| 项 | 选择 |
|---|---|
| 在场策略 | **固定舞台**（视口锁定 Grid）；非 sticky-only、非全屏背景人像 |
| 历史 | 仅在中部字幕区内滚动，不增高整页 |
| 底栏 | 录音/打字 dock 固定底部；变高时压缩 Persona 行，人像等比缩小但仍完整可见 |
| 人像最小行高 | 约 `38–42vh`（实现取 `minmax(38vh, 1fr)`） |

---

## 2. 布局契约

将 `.call-shell` 改为 **单屏纵向 Flex 通话壳**（视口锁定），页面本身不滚动；只有字幕/历史区滚动。

```
┌─────────────────────────────────────┐  ← call-shell: 100dvh, overflow:hidden, flex column
│ call-chrome（topbar + planHint）     │  flex: 0 0 auto
├─────────────────────────────────────┤
│ PersonaStage（弹性行，始终可见）      │  flex: 1 1 auto; min-height ≈ 38vh
├─────────────────────────────────────┤
│ call-captions（字幕 + 可折叠历史）    │  flex: 0 1 auto; max-height ≈ 34vh; overflow-y:auto
├─────────────────────────────────────┤
│ call-dock（录音 / 打字）              │  flex: 0 0 auto
└─────────────────────────────────────┘
```

（实现用 Flex 而非 Grid，以便「人像吃剩余高度、字幕区封顶滚动」更稳。）

---

## 3. 实现要点

### 3.1 结构（`kidora-web/src/app/cet/session/[id]/page.tsx`）

```tsx
<main className="shell call-shell" data-theme={themeId}>
  <div className="theme-motif" aria-hidden="true" />
  <header className="call-chrome">…topbar + plan…</header>
  <PersonaStage … />
  <div className="caption-strip call-captions">…</div>
  <div className="panel call-panel call-dock">…</div>
</main>
```

状态机、SSE、贴纸逻辑不变。

### 3.2 CSS（`globals.css`）

- `.call-shell`：`height: 100dvh`（`100svh` 兜底）、`overflow: hidden`、`display: flex; flex-direction: column`；`html/body:has(.call-shell)` 禁整页滚动；收紧整页 `padding`
- `.persona-stage`：`flex: 1 1 auto` 填满剩余高度；去掉写死的 `min-height: 58vh`
- `.persona-avatar`：相对舞台行高 + `aspect-ratio: 3/4`，保证半身卡不被裁出视口
- `.call-captions`：`max-height ≈ 34vh`、`overflow-y: auto`（矮屏再降）
- `.history-compact`：取消与整页竞争的巨大 `max-height: 48vh`；随字幕区滚动
- `.call-dock`：底栏，不参与页面级滚动

### 3.3 PersonaStage

组件行为（静帧/视频/贴纸）不变；仅依赖外层高度约束自适应。

---

## 4. 验收

- 默认态：半身像完整在视口上半区
- 「录音」/「打字」展开：人像仍完整可见（可略缩小）
- 「刚才说了 N 轮」展开：人像仍在；只有中部字幕区滚动
- 小屏与桌面均可；`prefers-reduced-motion` 行为不变

---

## 5. 文档同步

- 本规格
- [ui-design.md](../../ui-design.md) §5：视口锁定通话壳；人像常在；历史仅区内滚动
