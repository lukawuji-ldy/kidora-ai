package com.wuji.kidora.ai.cet.core.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafetyGuardTest.
 *
 * @author liudy
 */
class SafetyGuardTest {

    @Test
    void l0HardBlocksSelfHarm() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        SafetyDecision d = guard.checkL0("please kill yourself now");
        assertEquals(SafetyAction.HARD_BLOCK, d.action());
        assertEquals(SafetyDecision.POLICY_VERSION, d.policyVersion());
    }

    @Test
    void l0SoftBlocksPolitics() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        SafetyDecision d = guard.checkL0("Who is the president?");
        assertEquals(SafetyAction.SOFT_BLOCK, d.action());
    }

    @Test
    void l0AllowsSafeTopic() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        assertTrue(guard.checkL0("I have a cute cat").action() == SafetyAction.ALLOW);
    }

    @Test
    void parseModelDecisionHard() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        SafetyDecision d = guard.parseModelDecision("{\"action\":\"HARD_BLOCK\",\"reason\":\"violence\"}");
        assertEquals(SafetyAction.HARD_BLOCK, d.action());
    }

    @Test
    void parseInvalidJsonFailClosed() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        SafetyDecision d = guard.parseModelDecision("not-json");
        assertEquals(SafetyAction.SOFT_BLOCK, d.action());
        assertEquals("L1_UNAVAILABLE", d.eventType());
    }

    @Test
    void parseEmptyFailClosed() {
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), null, new ObjectMapper());
        SafetyDecision d = guard.parseModelDecision("  ");
        assertEquals(SafetyAction.SOFT_BLOCK, d.action());
    }

    @Test
    void modelThrowFailClosed() {
        ModelRouter broken = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                throw new RuntimeException("llm down");
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), broken, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        SafetyDecision d = guard.checkInput("hello cat", ctx);
        assertEquals(SafetyAction.SOFT_BLOCK, d.action());
        assertEquals("L1_UNAVAILABLE", d.eventType());
    }
}
