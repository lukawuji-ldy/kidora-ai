# Task 3 Report: 文档与规格状态

## Status

**DONE**

## Summary

Updated `docs/cet-tutor-design.md` §4 纠错话术 to align with the zh-scaffold + TTS parenthetical rules from spec §2–3 (中文脚手架触发、气泡括号保留、TTS 剥括号朗读中英正文). Marked `docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md` status as **Implemented（2026-09-10）**.

## Files Modified

| File | Change |
|------|--------|
| `docs/cet-tutor-design.md` | Replaced 纠错话术 bullets (L90–96): added 中文脚手架, revised 气泡/TTS rules |
| `docs/superpowers/specs/2026-09-10-cet-tutor-zh-scaffold-tts-design.md` | Status line: Accepted → Implemented（2026-09-10） |

## Step-by-Step Execution

### Step 1: Replace 纠错话术 section

Replaced four bullets with five bullets per brief: kept implicit/explicit correction; added 中文脚手架 (A0/A1 / 中文求助 / 显式纠错); updated 气泡 to allow `(中文注释)` and forbid stacked EN+parenthetical translations; updated TTS to read zh+en body via `speakableForTts` while pronunciation eval uses separate English `referenceText`.

**Not modified (per user override):** `docs/cet-lesson-flow.md`

### Step 2: Update spec status

Changed header status from `Accepted（待实现；完成后改为 Implemented）` to `Implemented（2026-09-10）`.

### Step 3: Commit

**Skipped** — user override forbids git operations.

## Verification

| Check | Outcome |
|-------|---------|
| 中文脚手架三条触发 | Yes — A0/A1 默认 / 中文求助 / 显式纠错 |
| 禁止整段英文主句+括号翻译堆叠 | Yes |
| TTS 剥括号、读中英正文 | Yes |
| 发音评测独立 `referenceText` | Yes |
| Spec status Implemented | Yes — 2026-09-10 |
| `cet-lesson-flow.md` untouched | Yes |

## Concerns

None. Docs-only task; no runtime or test impact.
