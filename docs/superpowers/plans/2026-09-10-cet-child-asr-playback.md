# CET Child ASR + Local Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the child's ASR transcript in the CET chat bubble and allow replaying the local microphone recording.

**Architecture:** Server emits a new SSE event `asr.transcript` once after successful ASR (before tutor deltas). The web client keeps the WAV `base64` on the child bubble for local playback and replaces the placeholder `（语音）` when the ASR event arrives. No audio persistence.

**Tech Stack:** Java 17, Spring Boot 3.4.8 / WebFlux SSE, `cet-tutor-core` + `cet-tutor-server`, Next.js (`kidora-web`), JUnit 5 + Reactor `StepVerifier`.

**Spec:** [docs/superpowers/specs/2026-09-10-cet-child-asr-playback-design.md](../specs/2026-09-10-cet-child-asr-playback-design.md)

## Global Constraints

- JDK 17; Spring Boot 3.4.8; no parent POM version drift.
- Do **not** call MCP ASR twice for display.
- Do **not** persist child audio or upload to cloud storage.
- Child UI must not show `provider` / raw JSON / pronunciation scores.
- Java type-level Javadoc must include `@author liudy`.
- Same delivery must update `docs/ui-design.md` and `docs/cet-lesson-flow.md` SSE sequences.
- Commits: only when the user explicitly asks in this session (include staged file lists ready; skip `git commit` steps unless asked).

---

## File map

| File | Role |
|---|---|
| `cet-tutor-core/.../speech/CetStreamEvent.java` | Add `Type.ASR` + `asr(json)` factory |
| `cet-tutor-core/.../service/CetLessonService.java` | Resolve child + ASR meta once; prefix Flux with ASR; soft/hard paths too |
| `cet-tutor-core/.../service/CetLessonServiceTest.java` | Resolve + `asrEvent` / prefix unit tests |
| `cet-tutor-server/.../web/CetSessionController.java` | Map `ASR` → `asr.transcript` |
| `cet-tutor-server/.../web/CetSessionControllerTest.java` | Assert event name |
| `kidora-web/src/lib/api.ts` | Parse `asr.transcript`, `onAsr` handler |
| `kidora-web/src/app/cet/session/[id]/page.tsx` | Child bubble text + local play button |
| `docs/ui-design.md` | Child bubble + SSE consumer note |
| `docs/cet-lesson-flow.md` §5.2 | Insert `asr.transcript` in SSE sequence |
| `docs/superpowers/specs/2026-09-10-cet-child-asr-playback-design.md` | Status → Accepted |

---

### Task 1: `CetStreamEvent.ASR` + SSE name mapping

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/speech/CetStreamEvent.java`
- Modify: `cet-tutor-server/src/main/java/com/wuji/kidora/ai/cet/server/web/CetSessionController.java` (`toSse` switch)
- Modify: `cet-tutor-server/src/test/java/com/wuji/kidora/ai/cet/server/web/CetSessionControllerTest.java`

**Interfaces:**
- Produces: `CetStreamEvent.Type.ASR`, `CetStreamEvent.asr(String json)`, SSE event name `asr.transcript`

- [ ] **Step 1: Extend controller test (fail until enum + mapping exist)**

In `CetSessionControllerTest.toSse_mapsEventNames`, append:

```java
ServerSentEvent<String> asr = CetSessionController.toSse(
        CetStreamEvent.asr("{\"text\":\"Hi\",\"locale\":\"en-US\",\"provider\":\"stub\"}"));
assertEquals("asr.transcript", asr.event());
assertTrue(asr.data().contains("\"text\":\"Hi\""));
```

Add `import static org.junit.jupiter.api.Assertions.assertTrue;` if missing.

- [ ] **Step 2: Run test — expect compile/fail**

Run:

```bash
cd cet-tutor-server
mvn -q -Dtest=CetSessionControllerTest test
```

Expected: compile error (`asr` / `ASR` missing) or assertion fail.

- [ ] **Step 3: Implement event + mapping**

In `CetStreamEvent.Type` add `ASR`.

Add factory:

```java
/**
 * ASR 识别结果 JSON。
 *
 * @param json payload
 * @return 事件
 */
public static CetStreamEvent asr(String json) {
    return new CetStreamEvent(Type.ASR, json == null ? "{}" : json);
}
```

In `CetSessionController.toSse`:

```java
case ASR -> "asr.transcript";
```

(keep existing cases unchanged)

- [ ] **Step 4: Re-run test — expect PASS**

```bash
cd cet-tutor-server
mvn -q -Dtest=CetSessionControllerTest test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit (only if user asked)**

```bash
git add cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/speech/CetStreamEvent.java \
  cet-tutor-server/src/main/java/com/wuji/kidora/ai/cet/server/web/CetSessionController.java \
  cet-tutor-server/src/test/java/com/wuji/kidora/ai/cet/server/web/CetSessionControllerTest.java
git commit -m "$(cat <<'EOF'
feat(cet): add asr.transcript SSE event type

EOF
)"
```

---

### Task 2: Resolve child once + build ASR event (core)

**Files:**
- Modify: `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java`
- Modify: `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java`

**Interfaces:**
- Consumes: `SpeechToolPort.AsrResult(text, confidence, provider)`, `CetStreamEvent.asr`
- Produces:
  - `record ChildTextResolve(String text, Optional<SpeechToolPort.AsrResult> asr, String locale)`
  - `static ChildTextResolve resolveChild(TurnInput input, SpeechToolPort port)`
  - `static Optional<CetStreamEvent> asrTranscriptEvent(ChildTextResolve resolved)`
  - `static Flux<CetStreamEvent> withAsrPrefix(ChildTextResolve resolved, Flux<CetStreamEvent> body)`
- Deprecate/remove direct callers of old `resolveChildText` returning `String` — replace with `resolveChild(...).text()` (keep a thin `resolveChildText` delegating to `.text()` **only if** external callers exist; currently only tests + `streamTurn`).

- [ ] **Step 1: Write failing unit tests**

Add to `CetLessonServiceTest`:

```java
@Test
void resolveChild_audioKeepsAsrMetaWithoutSecondCall() {
    int[] asrCalls = {0};
    SpeechToolPort port = new SpeechToolPort() {
        @Override
        public Optional<AsrResult> asr(String audioBase64, String locale) {
            asrCalls[0]++;
            return Optional.of(new AsrResult("Hello.", 0.9, "stub"));
        }
        @Override
        public Optional<TtsResult> tts(String text, String voice, String locale) {
            return Optional.empty();
        }
        @Override
        public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
            return Optional.empty();
        }
    };
    CetLessonService.ChildTextResolve r = CetLessonService.resolveChild(
            new TurnInput(null, "AAAA", "en-US", null), port);
    assertEquals("Hello.", r.text());
    assertTrue(r.asr().isPresent());
    assertEquals("stub", r.asr().get().provider());
    assertEquals("en-US", r.locale());
    assertEquals(1, asrCalls[0]);
}

@Test
void resolveChild_plainTextHasNoAsr() {
    CetLessonService.ChildTextResolve r = CetLessonService.resolveChild(TurnInput.ofText("Hi"), null);
    assertEquals("Hi", r.text());
    assertTrue(r.asr().isEmpty());
}

@Test
void asrTranscriptEvent_onlyWhenFromAudio() {
    assertTrue(CetLessonService.asrTranscriptEvent(
            new CetLessonService.ChildTextResolve("Hi", Optional.empty(), null)).isEmpty());

    Optional<CetStreamEvent> ev = CetLessonService.asrTranscriptEvent(
            new CetLessonService.ChildTextResolve(
                    "Hello.",
                    Optional.of(new SpeechToolPort.AsrResult("Hello.", 0.9, "stub")),
                    "en-US"));
    assertTrue(ev.isPresent());
    assertEquals(CetStreamEvent.Type.ASR, ev.get().type());
    assertTrue(ev.get().data().contains("\"text\":\"Hello.\""));
    assertTrue(ev.get().data().contains("\"locale\":\"en-US\""));
    assertTrue(ev.get().data().contains("\"provider\":\"stub\""));
}

@Test
void withAsrPrefix_prependsThenBody() {
    CetLessonService.ChildTextResolve r = new CetLessonService.ChildTextResolve(
            "Hello.",
            Optional.of(new SpeechToolPort.AsrResult("Hello.", 0.9, "stub")),
            "en-US");
    StepVerifier.create(CetLessonService.withAsrPrefix(r, Flux.just(CetStreamEvent.delta("ok"))))
            .assertNext(e -> assertEquals(CetStreamEvent.Type.ASR, e.type()))
            .assertNext(e -> {
                assertEquals(CetStreamEvent.Type.DELTA, e.type());
                assertEquals("ok", e.data());
            })
            .verifyComplete();
}

@Test
void withAsrPrefix_textOnlyPassesBody() {
    CetLessonService.ChildTextResolve r =
            new CetLessonService.ChildTextResolve("Hi", Optional.empty(), null);
    StepVerifier.create(CetLessonService.withAsrPrefix(r, Flux.just(CetStreamEvent.delta("ok"))))
            .assertNext(e -> assertEquals("ok", e.data()))
            .verifyComplete();
}
```

Update existing `resolveChildText_*` tests to call `resolveChild(...).text()` **or** keep `resolveChildText` as:

```java
static String resolveChildText(TurnInput input, SpeechToolPort port) {
    return resolveChild(input, port).text();
}
```

Prefer keeping the thin wrapper so old tests still compile; new tests use `resolveChild`.

- [ ] **Step 2: Run tests — expect fail**

```bash
cd cet-tutor-core
mvn -q -Dtest=CetLessonServiceTest test
```

Expected: compile errors for missing types/methods.

- [ ] **Step 3: Implement resolve + helpers**

Add nested record near other helpers in `CetLessonService`:

```java
/**
 * 儿童输入解析结果（语音路径携带 ASR，避免二次识别）。
 *
 * @param text   入模/展示文本
 * @param asr    语音路径非空
 * @param locale 请求 locale（可空）
 * @author liudy
 */
public record ChildTextResolve(String text, Optional<SpeechToolPort.AsrResult> asr, String locale) {
}
```

Implement `resolveChild` (move logic from current `resolveChildText`):

```java
static ChildTextResolve resolveChild(TurnInput input, SpeechToolPort port) {
    String locale = input == null ? null : input.locale();
    if (input != null && StringUtils.hasText(input.audioBase64())) {
        if (port == null) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "语音输入需要启用 MCP 语音能力");
        }
        SpeechToolPort.AsrResult asr = port.asr(input.audioBase64(), locale)
                .filter(a -> StringUtils.hasText(a.text()))
                .orElseThrow(() -> new KidoraException(ErrorCode.BAD_REQUEST, "ASR 未能识别语音"));
        return new ChildTextResolve(asr.text().trim(), Optional.of(asr), locale);
    }
    if (input != null && StringUtils.hasText(input.text())) {
        return new ChildTextResolve(input.text().trim(), Optional.empty(), locale);
    }
    throw new KidoraException(ErrorCode.BAD_REQUEST, "text 或 audioBase64 必填其一");
}

static String resolveChildText(TurnInput input, SpeechToolPort port) {
    return resolveChild(input, port).text();
}

static Optional<CetStreamEvent> asrTranscriptEvent(ChildTextResolve resolved) {
    if (resolved == null || resolved.asr().isEmpty()) {
        return Optional.empty();
    }
    SpeechToolPort.AsrResult a = resolved.asr().get();
    String json = "{\"text\":\"" + jsonEscape(nullToEmpty(a.text()))
            + "\",\"locale\":\"" + jsonEscape(nullToEmpty(resolved.locale()))
            + "\",\"provider\":\"" + jsonEscape(nullToEmpty(a.provider())) + "\"}";
    return Optional.of(CetStreamEvent.asr(json));
}

static Flux<CetStreamEvent> withAsrPrefix(ChildTextResolve resolved, Flux<CetStreamEvent> body) {
    Optional<CetStreamEvent> head = asrTranscriptEvent(resolved);
    if (head.isEmpty()) {
        return body;
    }
    return Flux.just(head.get()).concatWith(body);
}
```

Note: `jsonEscape` / `nullToEmpty` are currently `private static` — keep them; new methods in same class can call them. If `jsonEscape` visibility blocks tests, leave as-is (tests only assert via public helpers).

- [ ] **Step 4: Wire `streamTurn` to use resolve + prefix**

In `streamTurn`, replace:

```java
String childText = resolveChildText(input, speechToolPort.getIfAvailable());
```

with:

```java
ChildTextResolve resolved = resolveChild(input, speechToolPort.getIfAvailable());
String childText = resolved.text();
```

For **hard block** return path, change:

```java
return Flux.error(new KidoraException(ErrorCode.CET_SAFETY_BLOCKED, "..."));
```

to:

```java
return withAsrPrefix(resolved, Flux.error(new KidoraException(ErrorCode.CET_SAFETY_BLOCKED, "这条内容不太合适，我们换个话题吧")));
```

For **soft redirect**:

```java
return withAsrPrefix(resolved, Flux.just(CetStreamEvent.delta(softRedirect)));
```

For **normal body** (after building `body` Flux):

```java
return withAsrPrefix(resolved, body.concatWith(Flux.defer(() -> Flux.fromIterable(
        maybeStageEvaluate(userId, lessonSessionId, turnIndex, planJson, ctx))));
```

Do **not** call `port.asr` again anywhere in this method.

- [ ] **Step 5: Run core tests — expect PASS**

```bash
cd cet-tutor-core
mvn -q -Dtest=CetLessonServiceTest test
```

Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit (only if user asked)**

```bash
git add cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java \
  cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java
git commit -m "$(cat <<'EOF'
feat(cet): emit ASR transcript once per voice turn

EOF
)"
```

---

### Task 3: `kidora-web` consume ASR + child playback

**Files:**
- Modify: `kidora-web/src/lib/api.ts`
- Modify: `kidora-web/src/app/cet/session/[id]/page.tsx`

**Interfaces:**
- Consumes: SSE `asr.transcript` JSON `{ text, locale?, provider? }`
- Produces: `AsrPayload`, `SseHandlers.onAsr`, child bubble with local `audioBase64`

- [ ] **Step 1: Extend `api.ts`**

```ts
export type AsrPayload = {
  text?: string;
  locale?: string;
  provider?: string;
};

export type SseHandlers = {
  onDelta?: (text: string) => void;
  onSafety?: (text: string) => void;
  onError?: (text: string) => void;
  onTts?: (payload: TtsPayload) => void;
  onAsr?: (payload: AsrPayload) => void;
  onPronunciation?: (payload: PronunciationPayload) => void;
  onDone?: () => void;
};
```

In the `data:` branch of `postSse`, add:

```ts
else if (eventName === "asr.transcript") {
  try {
    handlers.onAsr?.(JSON.parse(data) as AsrPayload);
  } catch {
    handlers.onError?.("ASR 数据解析失败");
  }
}
```

- [ ] **Step 2: Update session page — `streamTurn` signature**

Change `streamTurn` to accept optional child audio:

```ts
async function streamTurn(
  body: { text?: string; audioBase64?: string; locale?: string; referenceText?: string },
  childLabel: string,
  childAudio?: { audioBase64: string; mimeType: string },
) {
  // ...
  setBubbles((prev) => [
    ...prev,
    {
      id: childId,
      role: "child",
      text: childLabel,
      ...(childAudio
        ? { audioBase64: childAudio.audioBase64, mimeType: childAudio.mimeType }
        : {}),
    },
    { id: tutorId, role: "tutor", text: "" },
  ]);
  // in postSse handlers:
  onAsr: (payload) => {
    const t = (payload.text || "").trim();
    if (!t) return;
    setBubbles((prev) =>
      prev.map((b) => (b.id === childId ? { ...b, text: t } : b)),
    );
  },
}
```

Voice send path:

```ts
await streamTurn(
  {
    audioBase64: capture.base64,
    locale: "en-US",
    ...(referenceText ? { referenceText } : {}),
  },
  "（语音）",
  { audioBase64: capture.base64, mimeType: capture.mimeType || "audio/wav" },
);
```

Text send path unchanged: `await streamTurn({ text: childText }, childText);`

- [ ] **Step 3: Child play button in bubble render**

Rename or alias shared stop/play for clarity (optional): keep `stopTutorVoice` / `playTutorVoice` but allow child bubbles to call the same audio path (no SpeechSynthesis fallback needed when `audioBase64` present).

In the bubble map, show play for **child or tutor** when audio (or tutor text) warrants it:

```tsx
{(b.role === "tutor" && b.text.trim()) ||
(b.role === "child" && b.audioBase64) ? (
  <button
    type="button"
    className={`bubble-speak${b.role === "tutor" && b.needsTap ? " needs-tap" : ""}`}
    aria-label={b.role === "tutor" ? "播放外教语音" : "播放我的录音"}
    onClick={() => {
      playTutorVoice({
        text: b.role === "tutor" ? b.text : undefined,
        audioBase64: b.audioBase64,
        mimeType: b.mimeType,
      });
      if (b.role === "tutor") {
        setBubbles((prev) =>
          prev.map((x) => (x.id === b.id ? { ...x, needsTap: false } : x)),
        );
      }
    }}
  >
    {b.role === "tutor" && b.needsTap ? "点这里听" : "播放"}
  </button>
) : null}
```

Child with ASR text still shows the play button when `audioBase64` is set.

- [ ] **Step 4: Manual smoke (no browser automation required in CI)**

Checklist (run locally against cet-tutor-server + mcp):

1. Record → bubble shows `（语音）` + 播放 → hear self.
2. After ASR → bubble text becomes transcript; 播放 still works.
3. Type a message → no play button.
4. Play child then tutor (or reverse) → only one audio at a time.

- [ ] **Step 5: Commit (only if user asked)**

```bash
git add kidora-web/src/lib/api.ts kidora-web/src/app/cet/session/[id]/page.tsx
git commit -m "$(cat <<'EOF'
feat(web): show child ASR text and local recording playback

EOF
)"
```

---

### Task 4: Docs sync + mark spec Accepted

**Files:**
- Modify: `docs/ui-design.md` (§5, §6, §10)
- Modify: `docs/cet-lesson-flow.md` (§5.2 SSE line)
- Modify: `docs/superpowers/specs/2026-09-10-cet-child-asr-playback-design.md` (status)

**Interfaces:** none (docs only)

- [ ] **Step 1: Update `ui-design.md`**

In §5 / message bullets, state:

- 儿童语音气泡：先显示「（语音）」+ 本地「播放」；收到 `asr.transcript` 后替换为识别文案；录音仅浏览器内存，不落库。

In §6, add: 消费 `asr.transcript`（语音轮）。

In §10 voice row, replace「儿童气泡显示「（语音）」」with「儿童气泡：ASR 文案 + 本地录音回放（`asr.transcript`）」。

- [ ] **Step 2: Update `cet-lesson-flow.md` §5.2**

Change the SSE line to:

```
→ SSE：[asr.transcript?] → message.delta* → [audio.tts?] → [pronunciation?] → done
   （有 audio 且 ASR 成功时必有 asr.transcript，排在 message.delta 前；硬拦：asr.transcript? → safety.block + error）
```

Also note soft redirect: `asr.transcript` then soft delta.

- [ ] **Step 3: Spec status**

Set design doc header status to: `Accepted`

- [ ] **Step 4: Commit (only if user asked)**

```bash
git add docs/ui-design.md docs/cet-lesson-flow.md \
  docs/superpowers/specs/2026-09-10-cet-child-asr-playback-design.md
git commit -m "$(cat <<'EOF'
docs(cet): document child ASR transcript SSE and UI

EOF
)"
```

---

## Spec coverage self-check

| Spec requirement | Task |
|---|---|
| ASR text in child bubble | T2 + T3 |
| Local recording playback | T3 |
| SSE `asr.transcript` before deltas | T1 + T2 |
| Soft/hard still emit ASR when recognized | T2 `withAsrPrefix` on those returns |
| No double ASR | T2 `resolveChild` once |
| No audio persistence | T3 local only; no backend store |
| Text-only unchanged | T2 no ASR event; T3 no play without audio |
| Docs ui-design + lesson-flow | T4 |
| Unit tests mapping + voice-only | T1 + T2 |

**Placeholder scan:** none intentional.  
**Type consistency:** `ChildTextResolve` / `asrTranscriptEvent` / `withAsrPrefix` / `asr.transcript` / `AsrPayload` aligned across tasks.
