# CET 开场英文问候 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A0/A1 开场变为热情英文问候+自我介绍、中文点题、英文下一问（带括号中文）；时段与人设名由服务端注入。

**Architecture:** `TutorLoop.generateOpening` 注入 `personaName` / `dayGreeting`；Flyway V13 改写 `cet.tutor.opening.*`；文档澄清开场 vs 练习轮下一问语言分流。

**Tech Stack:** Java 17、cet-tutor-core、Flyway（kidora-agent-server）、JUnit。

## Global Constraints

- 时区固定 `Asia/Shanghai`；`0–11` → `Good morning`，`12–17` → `Good afternoon`，其余 → `Good evening`
- 仅 A0/A1 强制三段式；语气极度热情友爱、短句、不堆感叹号
- Prompt 正文中文；`{{变量}}` 英文键；Javadoc `@author liudy`
- 本地 commit、不 push

---

### Task 1: TutorLoop 注入 helpers + 单测

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/tutor/TutorLoop.java`
- Create: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/tutor/TutorLoopTest.java`

- [x] **Step 1–4:** `dayGreetingForHour` / `dayGreetingNow` / `personaDisplayName`；`generateOpening` vars；单测 PASS
- [x] **Step 5: Commit** `feat: inject dayGreeting and personaName for CET opening`

---

### Task 2: Flyway V13 更新 Opening Prompt

**Files:**
- Create: `kidora-agent-server/src/main/resources/db/migration/V13__cet_opening_en_greeting.sql`

- [x] **Step 1:** UPDATE `cet.tutor.opening.system` / `.user` + sync published version
- [x] **Step 2: Commit** `chore: Flyway V13 CET opening English greeting prompts`

---

### Task 3: 文档与 spec 收尾

**Files:**
- Modify: `docs/cet-tutor-design.md`
- Modify: `docs/superpowers/specs/2026-09-10-cet-opening-en-greeting-design.md`
- Create: `docs/superpowers/plans/2026-09-10-cet-opening-en-greeting.md`

- [x] **Step 1:** 开场 vs 练习轮分流文档；spec → Implemented；本计划落盘
- [x] **Step 2: Commit** `docs: CET opening greeting rules and mark spec implemented`
