# Task 1 Report: TTS 单测与注释口径（中英正文 + 剥括号）

## Status

**DONE**

## Summary

Locked `speakableForTts` / `buildSpeechExtras` TTS behavior via unit tests and updated Javadoc to reflect **Chinese scaffolding + English examples minus Chinese parenthetical hints**. No production logic changes were required — existing regex already satisfied the new mixed-language assertions.

## Files Modified

| File | Change |
|------|--------|
| `cet-tutor-core/src/test/java/com/wuji/kidora/ai/cet/core/service/CetLessonServiceTest.java` | Extended `speakableForTts_stripsChineseParentheticalHints`; renamed `buildSpeechExtras_ttsUsesEnglishOnly` → `buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish` with mixed CN+EN stub |
| `cet-tutor-core/src/main/java/com/wuji/kidora/ai/cet/core/service/CetLessonService.java` | Javadoc only for `buildSpeechExtras` and `speakableForTts` |

## Step-by-Step Execution

### Step 1: TDD — extend / rename tests

- Added assertion to `speakableForTts_stripsChineseParentheticalHints`:
  - Input: `说得不错！你可以说"My dog is white"。What color is your dog? (你的狗是什么颜色？)`
  - Expected: `说得不错！你可以说"My dog is white"。What color is your dog?`
- Renamed `buildSpeechExtras_ttsUsesEnglishOnly` → `buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish` with matching TTS stub expectation.
- Retained English-only regression in `speakableForTts_stripsChineseParentheticalHints` (`Do you have a pet? (你有宠物吗？)` → `Do you have a pet?`).

### Step 2: Targeted test run

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-17.0.20.1"
mvn -q "-Dtest=CetLessonServiceTest#speakableForTts_stripsChineseParentheticalHints,CetLessonServiceTest#buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish" test
```

**Result:** PASS (both tests green on first run; no logic fix needed).

### Step 3: Javadoc update

Updated `buildSpeechExtras` and `speakableForTts` Javadoc per brief — now documents:
- TTS reads full bubble body (Chinese scaffold + English examples), strips parentheticals containing Chinese.
- Bubble text still sent intact to client.
- Pronunciation scoring uses separate `referenceText` with same parenthetical stripping.

### Step 4: Full test class run

```powershell
mvn -q -Dtest=CetLessonServiceTest test
```

**Result:** PASS (all tests in class).

### Step 5: Commit

**Skipped** — user override forbids git operations.

## Test Summary

| Command | Result |
|---------|--------|
| Targeted (2 methods) | PASS |
| Full `CetLessonServiceTest` | PASS |

## Self-Review

| Check | Outcome |
|-------|---------|
| Only brief-listed files modified | Yes |
| TDD order followed (tests first, then Javadoc) | Yes |
| Logic unchanged (tests passed without code fix) | Yes |
| Javadoc matches actual behavior | Yes |
| Regression case for English-only parenthetical retained | Yes (in `speakableForTts_stripsChineseParentheticalHints`) |
| `@author liudy` preserved on `speakableForTts` | Yes |

## Concerns

None. Existing regex `[（(][^）)]*[\u4e00-\u9fff][^）)]*[）)]` correctly strips Chinese parentheticals without removing Chinese main text or quoted English within the bubble.

## Environment Note

Default shell `JAVA_HOME` was unset (JRE 1.8 on PATH). Tests ran successfully with `$env:JAVA_HOME = C:\Users\Administrator\.jdks\ms-17.0.20.1`.

## Commits

None (user forbade commits).
