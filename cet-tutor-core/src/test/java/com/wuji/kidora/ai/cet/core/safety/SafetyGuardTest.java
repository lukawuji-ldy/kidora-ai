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
        // 非课堂短答模板，须走模型；失败 fail-closed
        SafetyDecision d = guard.checkInput("请帮我写一篇关于周末旅行的长作文吧谢谢", ctx);
        assertEquals(SafetyAction.SOFT_BLOCK, d.action());
        assertEquals("L1_UNAVAILABLE", d.eventType());
    }

    @Test
    void checkInput_fastAllowSkipsModel_forClassroomShortAnswer() {
        int[] calls = {0};
        ModelRouter counting = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                calls[0]++;
                return "{\"action\":\"ALLOW\",\"reason\":\"ok\"}";
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), counting, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        SafetyDecision d = guard.checkInput("I like dogs.", ctx);
        assertEquals(SafetyAction.ALLOW, d.action());
        assertEquals("L0_FAST_ALLOW", d.eventType());
        assertEquals(0, calls[0]);
    }

    @Test
    void checkInput_yesNoAndColorFastAllow() {
        ModelRouter mustNotCall = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                throw new AssertionError("model must not be called");
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), mustNotCall, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        assertEquals("L0_FAST_ALLOW", guard.checkInput("yes", ctx).eventType());
        assertEquals("L0_FAST_ALLOW", guard.checkInput("blue", ctx).eventType());
        assertEquals("L0_FAST_ALLOW", guard.checkInput("My dog is big.", ctx).eventType());
    }

    @Test
    void checkInput_hardStillBeatsFastAllow() {
        ModelRouter mustNotCall = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                throw new AssertionError("model must not be called");
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), mustNotCall, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        SafetyDecision d = guard.checkInput("please kill yourself now", ctx);
        assertEquals(SafetyAction.HARD_BLOCK, d.action());
        assertEquals("L0_HARD", d.eventType());
    }

    @Test
    void checkInput_nonEnglishStillHitsModel() {
        int[] calls = {0};
        ModelRouter counting = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                calls[0]++;
                return "{\"action\":\"ALLOW\",\"reason\":\"ok\"}";
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), counting, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        SafetyDecision d = guard.checkInput("我喜欢小狗", ctx);
        assertEquals(SafetyAction.ALLOW, d.action());
        assertEquals("L1_MODEL", d.eventType());
        assertEquals(1, calls[0]);
    }

    @Test
    void checkOutput_skipModel_forShortEncourageScaffold() {
        int[] calls = {0};
        ModelRouter counting = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                calls[0]++;
                return "{\"action\":\"ALLOW\",\"reason\":\"ok\"}";
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), counting, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        String tutor = "说得不错！你可以说\"My dog is white\"。What color is your dog? (你的狗是什么颜色？)";
        SafetyDecision d = guard.checkOutput(tutor, ctx);
        assertEquals(SafetyAction.ALLOW, d.action());
        assertEquals("L0_OUT_SKIP_MODEL", d.eventType());
        assertEquals(0, calls[0]);
    }

    @Test
    void checkOutput_longOrSuspiciousStillHitsModel() {
        int[] calls = {0};
        ModelRouter counting = new ModelRouter(null, null, null) {
            @Override
            public String callText(CallContext ctx, String systemPrompt, String userPrompt) {
                calls[0]++;
                return "{\"action\":\"ALLOW\",\"reason\":\"ok\"}";
            }
        };
        SafetyGuard guard = new SafetyGuard(new PromptTemplateService(null), counting, new ObjectMapper());
        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "t", "s", null, "u", "l", "CET", "s", "SAFETY");
        String withUrl = "不错！请打开 https://example.com 继续学习。What is this?";
        SafetyDecision d = guard.checkOutput(withUrl, ctx);
        assertEquals(SafetyAction.ALLOW, d.action());
        assertEquals("L1_MODEL", d.eventType());
        assertEquals(1, calls[0]);
    }
}
