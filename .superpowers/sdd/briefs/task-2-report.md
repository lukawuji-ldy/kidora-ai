# Task 2 Report: Flyway Prompt — 中文脚手架规则

## Status

**DONE**

## Summary

Created Flyway migration `V12__cet_tutor_zh_scaffold.sql` to update `cet.tutor.system` and `cet.tutor.opening.system` prompt templates with Chinese scaffolding rules (A0/A1 default, Chinese help requests, explicit correction) and TTS parenthetical-stripping guidance. Published `prompt_template_version` rows are synced using the same pattern as V10.

## Files Created

| File | Change |
|------|--------|
| `kidora-agent-server/src/main/resources/db/migration/V12__cet_tutor_zh_scaffold.sql` | New migration — two `UPDATE prompt_template` + one `UPDATE prompt_template_version` |

## Step-by-Step Execution

### Step 1: Create migration file

Created `V12__cet_tutor_zh_scaffold.sql` with complete SQL copied from the implementation plan (`docs/superpowers/plans/2026-09-10-cet-tutor-zh-scaffold-tts.md`, Task 2 Step 1). Content matches V10 structure: header comment, two template updates with `update_time = TIMESTAMPTZ '2026-09-10 10:00:00+00'`, and published-version sync join.

**Not modified (per brief):** `cet.tutor.user`, `cet.tutor.opening.user`.

### Step 2: Self-check SQL file

| Check | Outcome |
|-------|---------|
| Two `UPDATE prompt_template` (`cet.tutor.system`, `cet.tutor.opening.system`) | Yes — lines 3–20, 22–30 |
| One `UPDATE prompt_template_version` sync | Yes — lines 32–37 |
| 脚手架三条触发（A0/A1 / 中文求助 / 显式纠错） | Yes — rule 4 bullet list in `cet.tutor.system` |
| 禁止「英文主句 + 逐句括号翻译」堆叠 | Yes — rule 6 in `cet.tutor.system` |
| TTS 剥括号说明 | Yes — rule 6 (`系统朗读会去掉含中文的括号，并朗读中文正文与英文例句`); opening prompt (`系统朗读会去掉括号中文并朗读中英正文`) |
| Same published-version sync pattern as V10 | Yes — identical `UPDATE prompt_template_version` join clause |

### Step 3: Commit

**Skipped** — user override forbids git operations.

## Deployment Note

After applying this migration (restart `kidora-agent-server` or run Flyway against an existing `kidora_ai` database), runtime Tutor/Opening prompts will load the new Chinese scaffolding rules from `prompt_template`. No Java code changes in this task.

## Self-Review

| Check | Outcome |
|-------|---------|
| Only brief-listed file created | Yes |
| SQL matches plan/brief verbatim | Yes (source: plan doc; brief had minor encoding corruption) |
| No Java modifications | Yes |
| `update_time` bumped to `2026-09-10 10:00:00+00` | Yes |

## Concerns

None. Brief file (`task-2-brief.md`) had UTF-8 encoding artifacts; authoritative content taken from `docs/superpowers/plans/2026-09-10-cet-tutor-zh-scaffold-tts.md` which matches the intended SQL.

## Verification Not Run

Flyway apply against live PostgreSQL was not executed in this task (no DB connection in scope). Migration is static SQL only; apply on next `kidora-agent-server` startup or manual Flyway run.
