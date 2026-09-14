# CET 人设讯飞音色 Implementation Plan

> **For agentic workers:** 本计划已在同会话执行完毕；保留作交付对照。

**Goal:** 为 6 个 CET 人设 seed 讯飞 `vcn` 映射，并让管理台人设音色页支持双厂商编辑体验。

**Architecture:** 沿用既有 `cet_persona_voice` + primary vendor 解析；仅新增 Flyway seed 与 manage UI 文案/快捷选项，不改 MCP/运行时。

**Tech Stack:** PostgreSQL Flyway、Vue 3 + Element Plus（manage）

## Global Constraints

- 无 parent POM；版本锁定见 AGENTS.md
- 映射入库，禁止代码硬编码人设→vcn
- 文档同交付同步

---

### Task 1: Flyway seed + kidora-ai docs

- [x] `V25__cet_persona_voice_iflytek.sql`（40011–40016）
- [x] schema COMMENT、`cet-persona-design` §5.2、database/admin/mcp docs
- [x] 规格 `docs/superpowers/specs/2026-09-14-cet-persona-iflytek-voice-design.md`

### Task 2: manage UI + docs

- [x] `CetPersonaVoicesView.vue` 双厂商对照/默认全部/讯飞 vcn 下拉
- [x] Dashboard 文案
- [x] manage admin-design / ui-design
