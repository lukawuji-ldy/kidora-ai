package com.wuji.kidora.ai.cet.server.model;

import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.cet.server.CetTutorServerApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可选真实连通冒烟：验证 llm_chat_primary（DeepSeek）对 CET_TUTOR 可用。
 * 默认跳过；本地：{@code KIDORA_LLM_SMOKE=1 mvn -pl cet-tutor-server -Dtest=LlmChatPrimarySmokeTest test}
 * （需库中 ACTIVE 的 llm_chat_primary + 有效 key，并已 install kidora-agent-core）
 *
 * @author liudy
 */
@SpringBootTest(classes = CetTutorServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "KIDORA_LLM_SMOKE", matches = "1")
class LlmChatPrimarySmokeTest {

    private static final Logger log = LoggerFactory.getLogger(LlmChatPrimarySmokeTest.class);

    @Autowired
    private ModelRouter modelRouter;

    @Test
    void cetTutor_routesToChatPrimary_andResponds() {
        ModelRouter.RoutedClient routed = modelRouter.requireForCaller("CET_TUTOR");
        assertEquals("llm_chat_primary", routed.configId());
        assertTrue(routed.config().getModel() != null && !routed.config().getModel().isBlank());

        ModelRouter.CallContext ctx = new ModelRouter.CallContext(
                "smoke-trace", null, null, "smoke_user", "smoke_learner",
                "CET", "smoke_ref", "CET_TUTOR");
        long start = System.currentTimeMillis();
        String out;
        try {
            out = modelRouter.callText(ctx, "You are a connectivity ping.", "Reply with exactly: OK");
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "llm_chat_primary 调用失败（路由已指向该配置）。请核对管理台："
                            + "① base_url 宜为 https://api.deepseek.com（不要带 /v1，Spring AI 会再拼 /v1/chat/completions）；"
                            + "② model 须为官网有效 id（常见 deepseek-chat，确认 deepseek-flash 是否已上架）；"
                            + "③ API Key 与进程加密密钥一致。cause=" + e,
                    e);
        }
        long ms = System.currentTimeMillis() - start;
        log.info("llm_chat_primary smoke ok model={} latencyMs={} reply={}",
                routed.config().getModel(), ms, out == null ? "" : out.strip().substring(0, Math.min(80, out.strip().length())));
        assertFalse(out == null || out.isBlank(), "DeepSeek llm_chat_primary returned empty");
    }
}
