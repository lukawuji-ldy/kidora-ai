# CET Prop Singular Attribute Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attribute teaching captions never show multi-pet composite; upgrade generic `pet` to singular `dog`/`cat` via color cues; replace `pet.png` with a single-pet image.

**Architecture:** Frontend-only in `lessonProps.ts` after existing specific-pet extraction; static asset swap under `public/props/pets/`.

**Tech Stack:** TypeScript (`kidora-web`), selftest via `npx tsx`.

## Global Constraints

- No Tutor prompt migration in this plan
- No backend resolver changes
- Keep cat-or-dog dual display behavior
- Docs sync in same delivery

---

### Task 1: Failing selftests for attribute upgrade

**Files:**
- Modify: `kidora-web/src/lib/lessonProps.selftest.ts`

- [ ] Add cases: yellow pet → dog; brown/grey → cat; no cue + session cat → cat; generic pets still pet
- [ ] Run `npx tsx src/lib/lessonProps.selftest.ts` and confirm RED

### Task 2: Implement upgrade logic

**Files:**
- Modify: `kidora-web/src/lib/lessonProps.ts`

- [ ] Add `isAttributeTeachingCaption` + `resolveSingularPetStem`
- [ ] In `resolvePropStage`, upgrade sole `pet` asset on attribute captions
- [ ] Run selftest GREEN

### Task 3: Replace composite pet.png

**Files:**
- Modify: `kidora-web/public/props/pets/pet.png`

- [ ] Replace with single-pet image (one animal, plain background)

### Task 4: Docs

**Files:**
- Modify: `docs/superpowers/specs/2026-09-13-cet-prop-stage-library-design.md` §5.1
- Modify: `docs/superpowers/specs/2026-09-14-cet-prop-singular-attribute-design.md` status → Implemented
- Modify: `docs/ui-design.md` if prop stage rules are documented there
