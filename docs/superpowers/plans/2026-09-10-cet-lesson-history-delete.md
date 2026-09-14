# CET 上课记录硬删除 Implementation Plan

> **For agentic workers:** Implement per approved spec `docs/superpowers/specs/2026-09-10-cet-lesson-history-delete-design.md`.

**Goal:** CET 首页上课记录支持单条与批量硬删除（级联清子表）。

**Architecture:** `CetLessonService.deleteSessions` 校验归属（统一 404）+ 事务内 Repository 级联 DELETE；Web 管理态勾选 + 行内删除。

**Tech Stack:** Java 17 / Spring WebFlux CET API / Next.js `kidora-web`

## Global Constraints

- 硬删除不可恢复；不删 `llm_call_log`
- JWT userId 鉴权；批量上限 50、去重
- 不 commit / 不推远程（按用户要求）

## Tasks

- [x] Spec already approved
- [x] Backend normalize + deleteSessions + cascade repo + controller
- [x] Frontend manage mode + delete UX
- [x] Docs: cet-lesson-flow / README / ui-design / design status Accepted
