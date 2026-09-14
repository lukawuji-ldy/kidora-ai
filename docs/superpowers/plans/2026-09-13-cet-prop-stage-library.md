# CET Prop Stage Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teaching-mode left/right prop stage driven by session `propAssets` resolved from a local reusable prop library (ops-seeded; miss = no split).

**Architecture:** PostgreSQL `cet_prop_asset` + local disk files; open/GET session batch-resolves ≤5 `vocabHints` into `propAssets`; read-only `GET /api/cet/props/{lemma}`; kidora-web switches `personaFocus` ↔ `propFocus` from caption match against prefetched assets only.

**Tech Stack:** JDK 17, Spring Boot 3.4.8 WebFlux, PostgreSQL, Next.js (`kidora-web`)

**Spec:** [2026-09-13-cet-prop-stage-library-design.md](../specs/2026-09-13-cet-prop-stage-library-design.md)

## Global Constraints

- No per-turn SSE prop URLs; no CDN auto-fetch; no AI image gen; no `/api/admin/**` in this repo
- Props miss → skip (no enlarge); JWT auth on prop read API
- Versions locked to AGENTS.md; Javadoc `@author liudy`; docs sync in same delivery
- Commits only when user asks; stay on current branch (no feature branches)

## File map

| Path | Responsibility |
|---|---|
| `schema/2x_cet_prop_asset.sql` + `schema/all.sql` | DDL + comments |
| Flyway under `cet-tutor-server` or shared agent-server pattern (match repo) | Migration |
| `cet-tutor-core/.../repo/CetPropAssetRepository.java` | Batch lookup by lemma |
| `cet-tutor-core/.../props/PropAssetResolver.java` | Hint → lemma normalize + resolve |
| `cet-tutor-core/.../service/CetLessonService.java` | Attach `propAssets` on open/detail |
| `cet-tutor-server/.../web/CetPropController.java` | Stream local file |
| `cet-tutor-server` `application.yml` | `kidora.cet.props.local-dir` |
| `kidora-web` `lessonProps.ts` / `PersonaStage.tsx` / session page | Prefetch consume + split layout |
| Docs listed in spec §8 | Sync |

---

### Task 1: DDL + repository batch lookup

**Files:**
- Create: `schema/` prop asset SQL (number per repo convention)
- Create: `CetPropAssetRepository` + unit/integration-style JDBC test if pattern exists
- Modify: `schema/all.sql`

- [ ] Add `cet_prop_asset` with UNIQUE(`lemma`), indexes, COMMENT ON
- [ ] Implement `findActiveByLemmas(Collection<String>)` single `IN` query
- [ ] Test: empty IN → empty; hit/miss mix; DISABLED excluded
- [ ] Commit only if user requests

### Task 2: Resolver + session `propAssets`

**Files:**
- Create: `PropAssetResolver` (normalize aliases from DB + hints)
- Modify: `CetLessonService` `OpenResult` / `SessionDetail`
- Modify: `CetSessionController` JSON serialization
- Tests: `CetLessonServiceTest`

- [ ] Failing test: open with seeded lemmas returns `propAssets` urls `/api/cet/props/{lemma}`
- [ ] Failing test: unknown hints → omitted from list; open still succeeds
- [ ] Implement resolver + wire into open/GET
- [ ] Cap to vocabHints size (≤5)

### Task 3: Read-only prop file API + config

**Files:**
- Create: `CetPropController` (WebFlux)
- Modify: `application.yml` + properties class
- Test: controller/web test with temp dir fixture

- [ ] Config `kidora.cet.props.local-dir`
- [ ] GET streams file with content-type + Cache-Control; 404 for missing/DISABLED
- [ ] JWT required (same as CET session APIs)
- [ ] Seed script or README note for copying sample assets into local-dir + INSERT rows

### Task 4: Frontend propFocus split

**Files:**
- Modify: `lessonProps.ts`, `PersonaStage.tsx`, session `page.tsx`
- Tests: existing jest/vitest patterns if any for lessonProps

- [ ] Session types include `propAssets`
- [ ] Caption match against prefetched lemmas → `propFocus`; listening / no match → `personaFocus`
- [ ] Split layout ~38/62; active lemma large; ≤3 thumbs
- [ ] Image error removes lemma; all fail → personaFocus
- [ ] Default: do not fall back to static `/props/**` unless explicitly enabled

### Task 5: Docs sync

**Files:**
- Spec status → Implemented (when code done)
- `docs/ui-design.md`, `docs/cet-lesson-flow.md`, `docs/database-design.md`
- Brief index in `docs/cet-tutor-design.md` / `agents.md` if needed

- [ ] Document `propAssets`, prop read API, stage states, table
- [ ] Note ops upload lives in manage repo / seed script

## Verification

- [ ] `mvn -pl`/`-f` tests for cet-tutor-core and cet-tutor-server (module-local poms)
- [ ] Manual: seed dog/cat → open pets lesson → Look caption triggers split; resume conversation returns persona focus
- [ ] Empty library: open OK, no split
