package com.wuji.kidora.ai.cet.core.service;

import com.wuji.kidora.ai.cet.core.safety.SafetyAction;
import com.wuji.kidora.ai.cet.core.safety.SafetyDecision;
import com.wuji.kidora.ai.cet.core.tutor.TutorLoop;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
