package com.wuji.kidora.ai.cet.core.safety;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Safety L0 规则 + L1/L2 模型分类（模型不可用时 fail-closed：SOFT_BLOCK）。
 *
 * @author liudy
 */
@Component
public class SafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuard.class);

    private static final List<Pattern> HARD_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(kill yourself|suicide|self[- ]?harm)\\b"),
            Pattern.compile("(?i)(自杀|自残|色情|裸体|做爱)"),
            Pattern.compile("(?i)\\b(porn|xxx|nude)\\b"),
            Pattern.compile("(?i)\\b(bomb|terrorist|shoot\\s+up)\\b")
    );

    private static final List<Pattern> SOFT_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(president|election|religion|god|allah|jesus)\\b"),
            Pattern.compile("(?i)(总统|选举|宗教|耶稣|真主)")
    );

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public SafetyGuard(PromptTemplateService promptTemplateService,
                       ModelRouter modelRouter,
                       ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    public SafetyDecision checkInput(String text, ModelRouter.CallContext ctx) {
        SafetyDecision l0 = checkL0(text);
        if (!l0.action().equals(SafetyAction.ALLOW)) {
            return l0;
        }
        return checkModel(text, "INPUT", ctx);
    }

    public SafetyDecision checkOutput(String text, ModelRouter.CallContext ctx) {
        SafetyDecision l0 = checkL0(text);
        if (!l0.action().equals(SafetyAction.ALLOW)) {
            return l0;
        }
        return checkModel(text, "OUTPUT", ctx);
    }

    /**
     * 仅 L0（单测）。
     *
     * @author liudy
     */
    public SafetyDecision checkL0(String text) {
        if (!StringUtils.hasText(text)) {
            return SafetyDecision.allow();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (Pattern p : HARD_PATTERNS) {
            if (p.matcher(normalized).find() || p.matcher(text).find()) {
                return new SafetyDecision(SafetyAction.HARD_BLOCK, "L0_HARD", "sensitive content", null);
            }
        }
        for (Pattern p : SOFT_PATTERNS) {
            if (p.matcher(normalized).find() || p.matcher(text).find()) {
                return new SafetyDecision(SafetyAction.SOFT_BLOCK, "L0_SOFT", "redirect topic", null);
            }
        }
        return SafetyDecision.allow();
    }

    private SafetyDecision checkModel(String text, String direction, ModelRouter.CallContext ctx) {
        try {
            String system = promptTemplateService.loadAndRender("cet.safety.system", Map.of(),
                    "Classify child-safety. JSON: {action,reason,rewrite}");
            String user = promptTemplateService.loadAndRender("cet.safety.user",
                    Map.of("direction", direction, "text", text == null ? "" : text),
                    "Direction=" + direction + " Text=" + text);
            ModelRouter.CallContext safetyCtx = new ModelRouter.CallContext(
                    ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                    "CET", ctx.bizRefId(), "SAFETY");
            String raw = modelRouter.callText(safetyCtx, system, user);
            return parseModelDecision(raw);
        } catch (Exception e) {
            log.warn("safety model check fail-closed: {}", e.toString());
            return SafetyDecision.softUnavailable("L1_UNAVAILABLE");
        }
    }

    SafetyDecision parseModelDecision(String raw) {
        if (!StringUtils.hasText(raw)) {
            return SafetyDecision.softUnavailable("empty model response");
        }
        try {
            String json = extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            String action = node.path("action").asText("ALLOW").toUpperCase(Locale.ROOT);
            String reason = node.path("reason").asText("model");
            String rewrite = node.path("rewrite").isNull() ? null : node.path("rewrite").asText(null);
            SafetyAction sa = SafetyAction.valueOf(action);
            return new SafetyDecision(sa, "L1_MODEL", reason, rewrite);
        } catch (Exception e) {
            log.warn("parse safety json fail-closed: {}", e.toString());
            return SafetyDecision.softUnavailable("invalid safety json");
        }
    }

    public static String extractJson(String raw) {
        String t = raw.trim();
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }
}
