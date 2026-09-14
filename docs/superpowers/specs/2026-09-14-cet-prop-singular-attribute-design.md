# CET 道具：属性问句禁止多宠合图

- **日期：** 2026-09-14
- **仓库：** `kidora-ai`
- **状态：** Superseded（2026-09-14）
- **取代者：** [2026-09-14-cet-prop-realtime-stage-design.md](./2026-09-14-cet-prop-realtime-stage-design.md)
- **关联：** [2026-09-13-cet-prop-stage-library-design.md](./2026-09-13-cet-prop-stage-library-design.md)、[ui-design.md](../../ui-design.md)

> **已废弃。** 本文的颜色线索升级（`yellow→dog`、`brown|grey→cat`）绑死在当时素材的实际
> 颜色上，换素材即失效。选图权已收回后端 `PropStageDirector`：外教点名哪个词就展示哪张图，
> 不再有颜色线索与宠物特判，本文所述前端逻辑（`resolveSingularPetStem` 等）已整体删除。

## 1. 问题

教学句问「the pet 是什么颜色 / 又大又…」时，前端命中泛化 lemma `pet` 并展示 `pets/pet.png`（猫狗合图）。话术是单数属性，图是两只异色动物，孩子无法对齐标准答（如 yellow）。

## 2. 目标

1. **属性问句**（颜色 / 大小）命中泛化 `pet` 时，**禁止**展示多宠合图；改为单只具体实体 `dog` 或 `cat`。
2. **颜色线索优先**：`yellow/黄` → `dog`；`brown|grey|gray/棕|灰` → `cat`；无线索 → 本课 `propAssets` 中第一个具体实体，再默认 `dog`。
3. **换素材**：`pets/pet.png` 改为**单只宠物**图，仅保留给非属性的泛化字幕（如 `These are pets.`）。

## 3. 非目标

- 不改 Tutor Prompt / 不强制外教点名 dog/cat（后续可增强）
- 不按轮 SSE 推道具
- 不改后端 `PropAssetResolver` 别名表

## 4. 规则（前端 `resolvePropStage`）

在已有「字幕点名 cat/dog → 具体实体」之后增加：

| 条件 | 行为 |
|---|---|
| 属性教学句，且将展示的 lemma 仅为 `pet`（命中 / session 回退 / theme 回退） | 升级为单只 `dog`/`cat`（按 §2.2） |
| 非属性 + 泛化 pets | 仍用 `pet`（单只新图） |
| `cat or dog` 二选一 | 保持现有双实体逻辑，不变 |

属性句判定：字幕含颜色或大小线索（含 `what color` / `什么颜色` / `yellow` / `big` / `small` / `又大又` 等）。

## 5. 验收

1. `Look! The pet is big and yellow.` + 仅有 `pet` 资产 → `activeLemma=dog`，URL 为单只狗图  
2. `… brown pet …` / 灰/棕线索 → `cat`  
3. 属性句无颜色线索 + session 有 `cat` → `cat`；都无 → `dog`  
4. `Look! These are pets.` → 仍 `pet`，且图为单只  
5. `cat or dog` 二选一回归不变  
