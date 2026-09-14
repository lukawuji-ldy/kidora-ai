package com.wuji.kidora.ai.cet.core.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.port.MemoryWritePort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 开课 Planner（仅大循环；禁止在 Tutor 小循环调用）。
 *
 * @author liudy
 */
@Component
public class LessonPlanner {

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<MemoryWritePort> memoryWritePort;

    public LessonPlanner(PromptTemplateService promptTemplateService,
                         ModelRouter modelRouter,
                         ObjectMapper objectMapper,
                         ObjectProvider<MemoryWritePort> memoryWritePort) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.memoryWritePort = memoryWritePort;
    }

    public PlanResult plan(LearnerProfile profile, String topic, String personaId, ModelRouter.CallContext ctx) {
        String cefr = StringUtils.hasText(profile.cefrLevel()) ? profile.cefrLevel() : "A1";
        String persona = StringUtils.hasText(personaId) ? personaId
                : (StringUtils.hasText(profile.preferredPersona()) ? profile.preferredPersona() : "emma");
        String profileExtra = truncate(profile.extraJson(), 800);
        String semanticHits = loadSemanticHits(profile.learnerId());
        Map<String, String> vars = Map.of(
                "displayName", nullToEmpty(profile.displayName()),
                "ageBand", nullToEmpty(profile.ageBand()),
                "cefr", cefr,
                "personaId", persona,
                "topic", nullToEmpty(topic),
                "profileExtra", profileExtra,
                "semanticHits", semanticHits
        );
        String system = promptTemplateService.loadAndRender("cet.planner.system", vars,
                "Output JSON training plan for child English. Consider profileExtra and semanticHits.");
        String user = promptTemplateService.loadAndRender("cet.planner.user", vars,
                "Topic={{topic}} CEFR={{cefr}} profile={{profileExtra}} memory={{semanticHits}}");
        ModelRouter.CallContext planCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_PLAN");
        try {
            String raw = modelRouter.callText(planCtx, system, user);
            String json = SafetyGuard.extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            if (node instanceof ObjectNode objectNode) {
                PlanLearningHints.ensureChildGoals(objectNode, topic);
                json = objectMapper.writeValueAsString(objectNode);
            }
            String childSummary = node.path("childSummary").asText("Let's practice " + topic + "!");
            return new PlanResult(json, childSummary, persona, cefr);
        } catch (Exception e) {
            String fallback = defaultPlanJson(topic, persona, cefr);
            return new PlanResult(fallback, "我们来练习「" + topic + "」吧！", persona, cefr);
        }
    }

    private String loadSemanticHits(String learnerId) {
        MemoryWritePort port = memoryWritePort.getIfAvailable();
        if (port == null || !StringUtils.hasText(learnerId)) {
            return "";
        }
        List<String> hits = port.listRecentSemantic(learnerId, 5);
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        return hits.stream().map(s -> truncate(s, 120)).collect(Collectors.joining(" | "));
    }

    private static String truncate(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private String defaultPlanJson(String topic, String persona, String cefr) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("topic", topic);
        root.put("cefr", cefr);
        root.put("personaId", persona);
        ObjectNode objectives = root.putObject("objectives");
        ArrayNode vocabulary = objectives.putArray("vocabulary");
        vocabulary.add("hello");
        vocabulary.add("please");
        ArrayNode patterns = objectives.putArray("patterns");
        patterns.add("It's ...");
        patterns.add("big / small");
        patterns.add("What color...");
        patterns.add("I like...");
        ArrayNode stages = root.putArray("stages");
        stages.add(stage("warmup", "Warm-up", "Greet; name or choose one topic word (not yes/no only)", 2));
        stages.add(stage("vocab", "Vocabulary", "Point/name 3-5 words; mix color/size where relevant", 3));
        stages.add(stage("model", "Model", "Model short phrases; child repeats (Say: ...)", 2));
        stages.add(stage("dialog", "Dialog",
                "Vary asks: point, choose, compare, describe; avoid same-template Do-you-like chains", 5));
        stages.add(stage("wrapup", "Wrap-up", "Encourage and summarize one favorite phrase", 1));
        root.put("childSummary", "我们来练习「" + topic + "」吧！");
        PlanLearningHints.ensureChildGoals(root, topic);
        return root.toString();
    }

    private ObjectNode stage(String id, String name, String goal, int turns) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("id", id);
        n.put("name", name);
        n.put("goal", goal);
        n.put("targetTurns", turns);
        return n;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record PlanResult(String planJson, String childSummary, String personaId, String cefr) {
    }
}
