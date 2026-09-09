package com.wuji.kidora.ai.agent.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PromptTemplateServiceTest.
 *
 * @author liudy
 */
class PromptTemplateServiceTest {

    @Test
    void renderReplacesVariables() {
        PromptTemplateService service = new PromptTemplateService(null);
        String out = service.render("Hello {{ name }}, level={{cefr}}", Map.of(
                "name", "Amy",
                "cefr", "A1"));
        assertEquals("Hello Amy, level=A1", out);
    }

    @Test
    void loadAndRenderUsesFallbackWhenDbMissing() {
        PromptTemplateService service = new PromptTemplateService(null);
        String out = service.loadAndRender("missing.code", Map.of("topic", "pets"),
                "Talk about {{topic}}");
        assertEquals("Talk about pets", out);
    }
}
