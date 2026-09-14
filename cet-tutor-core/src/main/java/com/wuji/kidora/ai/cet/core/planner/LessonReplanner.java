package com.wuji.kidora.ai.cet.core.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.eval.SessionEvaluator;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 大循环 Re-Planner（禁止在 Tutor 小循环每轮调用）。
 *
 * @author liudy
 */
@Component
public class LessonReplanner {

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public LessonReplanner(PromptTemplateService promptTemplateService,
                           ModelRouter modelRouter,
                           ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据评测信号修订计划。
     *
     * @param currentPlanJson 当前计划
     * @param eval            评测结果
     * @param ctx             调用上下文
     * @return 新计划 JSON + 儿童摘要
     */
    public ReplanResult replan(String currentPlanJson, SessionEvaluator.EvalResult eval,
                               ModelRouter.CallContext ctx) {
        String focus = eval.focus() == null ? "" : String.join(",", eval.focus());
        Map<String, String> vars = Map.of(
                "planJson", nullToEmpty(currentPlanJson),
                "evalJson", nullToEmpty(eval.assessmentJson()),
                "focus", focus,
                "pauseNewVocab", String.valueOf(eval.pauseNewVocab())
        );
        String system = promptTemplateService.loadAndRender("cet.replan.system", vars,
                "Revise child English training plan JSON.");
        String user = promptTemplateService.loadAndRender("cet.replan.user", vars,
                "Plan={{planJson}} Eval={{evalJson}}");
        ModelRouter.CallContext replanCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_REPLAN");
        try {
            String raw = modelRouter.callText(replanCtx, system, user);
            String json = SafetyGuard.extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            if (StringUtils.hasText(eval.insertStageJson())) {
                json = mergeInsertStage(json, eval.insertStageJson());
                node = objectMapper.readTree(json);
            }
            if (node instanceof ObjectNode objectNode) {
                PlanLearningHints.ensureChildGoals(objectNode, objectNode.path("topic").asText("review"));
                json = objectMapper.writeValueAsString(objectNode);
            }
            String childSummary = node.path("childSummary").asText(
                    StringUtils.hasText(eval.childSummary()) ? eval.childSummary() : "我们换个方式继续练！");
            return new ReplanResult(json, childSummary);
        } catch (Exception e) {
            return new ReplanResult(fallbackPlan(currentPlanJson, eval),
                    StringUtils.hasText(eval.childSummary()) ? eval.childSummary() : "我们换个方式继续练！");
        }
    }

    private String mergeInsertStage(String planJson, String insertStageJson) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(planJson);
        JsonNode insert = objectMapper.readTree(insertStageJson);
        ArrayNode stages = root.withArray("stages");
        ObjectNode stage = objectMapper.createObjectNode();
        stage.put("id", insert.path("id").asText("micro_drill"));
        stage.put("name", insert.path("name").asText("Micro drill"));
        stage.put("goal", insert.path("goal").asText("review"));
        stage.put("targetTurns", insert.path("targetTurns").asInt(3));
        stages.insert(0, stage);
        return root.toString();
    }

    private String fallbackPlan(String currentPlanJson, SessionEvaluator.EvalResult eval) {
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(
                    StringUtils.hasText(currentPlanJson) ? currentPlanJson : "{}");
            if (!root.has("topic")) {
                root.put("topic", "review");
            }
            root.put("childSummary", StringUtils.hasText(eval.childSummary())
                    ? eval.childSummary() : "我们换个方式继续练！");
            ArrayNode stages = root.withArray("stages");
            ObjectNode micro = objectMapper.createObjectNode();
            micro.put("id", "micro_drill");
            micro.put("name", "Micro drill");
            List<String> focus = eval.focus() == null ? List.of() : eval.focus();
            micro.put("goal", focus.isEmpty() ? "review basics" : focus.stream().collect(Collectors.joining(", ")));
            micro.put("targetTurns", 3);
            stages.insert(0, micro);
            if (eval.pauseNewVocab()) {
                JsonNode objectives = root.path("objectives");
                if (objectives.isObject()) {
                    ((ObjectNode) objectives).putArray("vocabulary");
                }
            }
            PlanLearningHints.ensureChildGoals(root, root.path("topic").asText("review"));
            return root.toString();
        } catch (Exception e) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("topic", "review");
            root.put("cefr", "A1");
            root.put("personaId", "emma");
            root.putArray("objectives").add("Review basics");
            ArrayNode stages = root.putArray("stages");
            ObjectNode s = stages.addObject();
            s.put("id", "micro_drill");
            s.put("name", "Micro drill");
            s.put("goal", "review");
            s.put("targetTurns", 3);
            root.put("childSummary", "我们换个方式继续练！");
            PlanLearningHints.ensureChildGoals(root, "review");
            return root.toString();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * Re-plan 结果。
     *
     * @param planJson     新计划
     * @param childSummary 儿童摘要
     * @author liudy
     */
    public record ReplanResult(String planJson, String childSummary) {
    }
}
