package com.wuji.kidora.ai.cet.core.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

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

    public LessonPlanner(PromptTemplateService promptTemplateService,
                         ModelRouter modelRouter,
                         ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    public PlanResult plan(LearnerProfile profile, String topic, String personaId, ModelRouter.CallContext ctx) {
        String cefr = StringUtils.hasText(profile.cefrLevel()) ? profile.cefrLevel() : "A1";
        String persona = StringUtils.hasText(personaId) ? personaId
                : (StringUtils.hasText(profile.preferredPersona()) ? profile.preferredPersona() : "emma");
        Map<String, String> vars = Map.of(
                "displayName", profile.displayName(),
                "ageBand", nullToEmpty(profile.ageBand()),
                "cefr", cefr,
                "personaId", persona,
                "topic", topic
        );
        String system = promptTemplateService.loadAndRender("cet.planner.system", vars,
                "Output JSON training plan for child English.");
        String user = promptTemplateService.loadAndRender("cet.planner.user", vars,
                "Topic={{topic}} CEFR={{cefr}}");
        ModelRouter.CallContext planCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_PLAN");
        try {
            String raw = modelRouter.callText(planCtx, system, user);
            String json = SafetyGuard.extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            String childSummary = node.path("childSummary").asText("Let's practice " + topic + "!");
            return new PlanResult(json, childSummary, persona, cefr);
        } catch (Exception e) {
            String fallback = defaultPlanJson(topic, persona, cefr);
            return new PlanResult(fallback, "我们来练习「" + topic + "」吧！", persona, cefr);
        }
    }

    private String defaultPlanJson(String topic, String persona, String cefr) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("topic", topic);
        root.put("cefr", cefr);
        root.put("personaId", persona);
        ArrayNode objectives = root.putArray("objectives");
        objectives.add("Warm up greetings");
        objectives.add("Practice topic vocabulary");
        ArrayNode stages = root.putArray("stages");
        stages.add(stage("warmup", "Warm-up", "Greet and introduce topic", 2));
        stages.add(stage("vocab", "Vocabulary", "Learn 3-5 words", 3));
        stages.add(stage("model", "Model", "Listen to tutor model", 2));
        stages.add(stage("dialog", "Dialog", "Practice dialog", 5));
        stages.add(stage("wrapup", "Wrap-up", "Encourage and summarize", 1));
        root.put("childSummary", "我们来练习「" + topic + "」吧！");
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
