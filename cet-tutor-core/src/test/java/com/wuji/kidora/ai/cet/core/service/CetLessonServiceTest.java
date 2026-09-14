package com.wuji.kidora.ai.cet.core.service;

import com.wuji.kidora.ai.cet.core.props.PropAssetResolver;
import com.wuji.kidora.ai.cet.core.props.PropStageDirector;
import com.wuji.kidora.ai.cet.core.safety.SafetyAction;
import com.wuji.kidora.ai.cet.core.safety.SafetyDecision;
import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import com.wuji.kidora.ai.cet.core.speech.SpeechToolPort;
import com.wuji.kidora.ai.cet.core.speech.TurnInput;
import com.wuji.kidora.ai.cet.core.speech.TurnTimingCollector;
import com.wuji.kidora.ai.cet.core.tutor.TutorLoop;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CetLessonServiceTest — 输出闸门与 SSE 分块（无 JDBC）。
 *
 * @author liudy
 */
class CetLessonServiceTest {

    @Test
    void outputHardReplacesUnsafeText() {
        String raw = "here is porn content for kids";
        SafetyDecision hard = new SafetyDecision(SafetyAction.HARD_BLOCK, "L0_HARD", "bad", null);
        String gated = CetLessonService.applyOutputGate(hard, raw);
        assertFalse(gated.toLowerCase().contains("porn"));
        assertEquals("Great try! Let's practice a safer sentence together.", gated);
    }

    @Test
    void outputSoftAlsoReplaces() {
        SafetyDecision soft = SafetyDecision.softUnavailable("L1_UNAVAILABLE");
        String gated = CetLessonService.applyOutputGate(soft, "maybe unsafe");
        assertEquals("Great try! Let's practice a safer sentence together.", gated);
    }

    @Test
    void outputRewriteUsesRewriteText() {
        SafetyDecision rw = new SafetyDecision(SafetyAction.REWRITE, "L1_MODEL", "fix", "Let's talk about cats!");
        assertEquals("Let's talk about cats!", CetLessonService.applyOutputGate(rw, "raw"));
    }

    @Test
    void outputAllowKeepsRaw() {
        assertEquals("Nice job!", CetLessonService.applyOutputGate(SafetyDecision.allow(), "Nice job!"));
    }

    @Test
    void chunkForSseDoesNotLeakBeforeGate() {
        String safe = CetLessonService.applyOutputGate(
                new SafetyDecision(SafetyAction.HARD_BLOCK, "L0_HARD", "x", null),
                "unsafe bomb recipe");
        StepVerifier.create(TutorLoop.chunkForSse(safe).reduce("", String::concat))
                .expectNext(safe)
                .verifyComplete();
        assertFalse(safe.contains("bomb"));
    }

    @Test
    void ownershipMismatchThrowsForbidden() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.assertSessionOwned("u_a", "u_b"));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void ownershipMatchPasses() {
        CetLessonService.assertSessionOwned("u_a", "u_a");
    }

    @Test
    void softInputReturnsRedirect() {
        SafetyDecision soft = SafetyDecision.softUnavailable("L1_UNAVAILABLE");
        assertEquals(CetLessonService.SOFT_INPUT_REDIRECT,
                CetLessonService.softInputRedirectOrEmpty(soft));
    }

    @Test
    void hardAndAllowDoNotSoftRedirect() {
        assertNull(CetLessonService.softInputRedirectOrEmpty(
                new SafetyDecision(SafetyAction.HARD_BLOCK, "L0_HARD", "x", null)));
        assertNull(CetLessonService.softInputRedirectOrEmpty(SafetyDecision.allow()));
    }

    @Test
    void resolveChildText_fromPlainText() {
        assertEquals("Hi", CetLessonService.resolveChildText(TurnInput.ofText("Hi"), null));
    }

    @Test
    void resolveChildText_emptyThrows() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.resolveChildText(new TurnInput(null, null, null, null), null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void resolveChildText_audioWithoutPortThrows() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.resolveChildText(new TurnInput(null, "AAAA", "en-US", null), null));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void resolveChildText_audioUsesAsr() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
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
        assertEquals("Hello.", CetLessonService.resolveChildText(
                new TurnInput(null, "AAAA", "en-US", null), port));
    }

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

    @Test
    void buildSpeechExtras_ttsThenPronunciation() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                return Optional.of(new TtsResult("", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                return Optional.of(new PronunciationResult(85, 88, 82, 90, "stub"));
            }
        };
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
                port, "Nice!", "AAAA", "en-US", "Hello.", null);
        assertEquals(2, extras.size());
        assertEquals(CetStreamEvent.Type.TTS, extras.get(0).type());
        assertTrue(extras.get(0).data().contains("\"provider\":\"stub\""));
        assertEquals(CetStreamEvent.Type.PRONUNCIATION, extras.get(1).type());
        assertTrue(extras.get(1).data().contains("\"overall\":85"));
    }

    @Test
    void assertOpeningAllowed_zeroTurnsOk() {
        CetLessonService.assertOpeningAllowed(0);
    }

    @Test
    void assertOpeningAllowed_rejectsWhenTurnsExist() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.assertOpeningAllowed(1));
        assertEquals(ErrorCode.CET_INVALID_STATE, ex.getErrorCode());
    }

    @Test
    void buildSpeechExtras_skipsPronunciationWithoutReference() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                throw new AssertionError("should not score");
            }
        };
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
                port, "Nice!", "AAAA", "en-US", null, null);
        assertEquals(1, extras.size());
        assertEquals(CetStreamEvent.Type.TTS, extras.get(0).type());
    }

    @Test
    void speakableForTts_stripsChineseParentheticalHints() {
        assertEquals("Do you have a pet?",
                CetLessonService.speakableForTts("Do you have a pet? (你有宠物吗？)"));
        assertEquals("I love dogs. Is your dog big or small?",
                CetLessonService.speakableForTts(
                        "I love dogs. (我喜欢狗狗。) Is your dog big or small? (你的狗狗是大还是小呢？)"));
        assertEquals("Nice!", CetLessonService.speakableForTts("Nice!"));
        assertEquals(
                "说得不错！你可以说\"My dog is white\"。What color is your dog?",
                CetLessonService.speakableForTts(
                        "说得不错！你可以说\"My dog is white\"。What color is your dog? (你的狗是什么颜色？)"));
    }

    @Test
    void speakableForTts_padsTrailingPauseWhenMissingPunctuation() {
        assertEquals("Hello friend.", CetLessonService.speakableForTts("Hello friend"));
    }

    @Test
    void buildSpeechExtras_ttsStripsParentheticalKeepsChineseAndEnglish() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                assertEquals(
                        "说得不错！你可以说\"My dog is white\"。What color is your dog?",
                        text);
                return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                return Optional.empty();
            }
        };
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
                port,
                "说得不错！你可以说\"My dog is white\"。What color is your dog? (你的狗是什么颜色？)",
                null, "en-US", null, null);
        assertEquals(1, extras.size());
    }

    @Test
    void buildSpeechExtras_passesMappedVoiceToTts() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                assertEquals("501009", voice);
                return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                return Optional.empty();
            }
        };
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
                port, "Hello!", null, "en-US", null, "501009");
        assertEquals(1, extras.size());
        assertEquals(CetStreamEvent.Type.TTS, extras.get(0).type());
    }

    @Test
    void buildSpeechExtras_passesNullVoiceWhenUnmapped() {
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                assertNull(voice);
                return Optional.of(new TtsResult("xx", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                return Optional.empty();
            }
        };
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtras(
                port, "Hello!", null, "en-US", null, null);
        assertEquals(1, extras.size());
    }

    @Test
    void normalizeDeleteIds_dedupesPreservesOrder() {
        List<String> ids = CetLessonService.normalizeDeleteIds(List.of("cls_a", "cls_b", "cls_a"));
        assertEquals(List.of("cls_a", "cls_b"), ids);
    }

    @Test
    void normalizeDeleteIds_emptyThrows() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.normalizeDeleteIds(List.of()));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void normalizeDeleteIds_blankEntriesIgnoredThenEmptyThrows() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.normalizeDeleteIds(List.of("  ", "")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void normalizeDeleteIds_overLimitThrows() {
        List<String> many = java.util.stream.IntStream.range(0, 51)
                .mapToObj(i -> "cls_" + i)
                .toList();
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.normalizeDeleteIds(many));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    void assertOwnedForDelete_mismatchIsNotFound() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> CetLessonService.assertOwnedForDelete("u_a", "u_b"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void assertOwnedForDelete_matchPasses() {
        CetLessonService.assertOwnedForDelete("u_a", "u_a");
    }

    @Test
    void buildSpeechExtrasTimed_recordsSkipWhenPortNull() {
        TurnTimingCollector timing = new TurnTimingCollector("turn", "text");
        List<CetStreamEvent> extras = CetLessonService.buildSpeechExtrasTimed(
                null, "Hello", null, null, null, null, timing);
        assertTrue(extras.isEmpty());
        String json = timing.toJson();
        assertTrue(json.contains("\"tts\":true") || json.contains("\"tts\": true"));
        assertTrue(json.contains("\"score\":true") || json.contains("\"score\": true"));
    }

    @Test
    void speechExtrasFlux_emitsTtsBeforeSlowScore_andTtsReadyExcludesScore() {
        int[] scoreCalls = {0};
        SpeechToolPort port = new SpeechToolPort() {
            @Override
            public Optional<AsrResult> asr(String audioBase64, String locale) {
                return Optional.empty();
            }

            @Override
            public Optional<TtsResult> tts(String text, String voice, String locale) {
                return Optional.of(new TtsResult("YQ==", "audio/wav", "stub"));
            }

            @Override
            public Optional<PronunciationResult> score(String audioBase64, String referenceText, String locale) {
                scoreCalls[0]++;
                try {
                    Thread.sleep(120);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Optional.of(new PronunciationResult(85, 88, 82, 90, "stub"));
            }
        };
        TurnTimingCollector timing = new TurnTimingCollector("turn", "voice");
        long[] readyAtFirstEvent = {-1L};
        StepVerifier.create(CetLessonService.speechExtrasFlux(
                        port, "Nice!", "AAAA", "en-US", "Hello.", null, timing))
                .assertNext(e -> {
                    assertEquals(CetStreamEvent.Type.TTS, e.type());
                    assertEquals(0, scoreCalls[0], "score must not run before audio.tts is emitted");
                    readyAtFirstEvent[0] = timing.ttsReadyMsOrMinusOne();
                    assertTrue(readyAtFirstEvent[0] >= 0, "ttsReady must be marked when TTS emits");
                })
                .assertNext(e -> {
                    assertEquals(CetStreamEvent.Type.PRONUNCIATION, e.type());
                    assertEquals(1, scoreCalls[0]);
                    long afterScore = timing.elapsedMs();
                    assertTrue(afterScore >= readyAtFirstEvent[0] + 80,
                            "score wall time must sit after ttsReady");
                    assertEquals(readyAtFirstEvent[0], timing.ttsReadyMsOrMinusOne());
                })
                .verifyComplete();
        String json = timing.toJson();
        assertTrue(json.contains("\"score\":false") || json.contains("\"score\": false"));
        assertTrue(timing.scoreMs() >= 80);
        assertTrue(timing.ttsReadyMsOrMinusOne() + timing.scoreMs() <= timing.elapsedMs() + 50);
    }

    @Test
    void propStageJson_serializesPropFocusWithOrderedAssets() {
        String json = CetLessonService.propStageJson(new PropStageDirector.PropStageView(
                PropStageDirector.LAYOUT_PROP_FOCUS, "dog",
                List.of(new PropAssetResolver.PropAssetView("dog", "pets", "/api/cet/props/dog"),
                        new PropAssetResolver.PropAssetView("cat", "pets", "/api/cet/props/cat"))));

        assertEquals("{\"layout\":\"propFocus\",\"activeLemma\":\"dog\",\"assets\":["
                + "{\"lemma\":\"dog\",\"theme\":\"pets\",\"url\":\"/api/cet/props/dog\"},"
                + "{\"lemma\":\"cat\",\"theme\":\"pets\",\"url\":\"/api/cet/props/cat\"}]}", json);
    }

    @Test
    void propStageJson_personaFocusHasNullActiveLemmaAndNoAssets() {
        String json = CetLessonService.propStageJson(new PropStageDirector.PropStageView(
                PropStageDirector.LAYOUT_PERSONA_FOCUS, null, List.of()));

        assertEquals("{\"layout\":\"personaFocus\",\"activeLemma\":null,\"assets\":[]}", json);
    }
}
