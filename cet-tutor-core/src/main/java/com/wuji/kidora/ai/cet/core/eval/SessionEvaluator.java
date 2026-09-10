package com.wuji.kidora.ai.cet.core.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话/阶段评测（MVP-3：产出 decision 驱动 Re-plan）。
 *
 * @author liudy
 */
@Component
public class SessionEvaluator {

    /**
     * 评测决策。
     */
    public enum Decision {
        CONTINUE,
        REPLAN,
        COMPLETE;

        /**
         * 解析决策字符串。
         *
         * @param raw 原始
         * @param fallback 缺省
         * @return 决策
         */
        public static Decision from(String raw, Decision fallback) {
            if (!StringUtils.hasText(raw)) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "continue" -> CONTINUE;
                case "replan" -> REPLAN;
                case "complete" -> COMPLETE;
                default -> fallback;
            };
        }
    }

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public SessionEvaluator(PromptTemplateService promptTemplateService,
                            ModelRouter modelRouter,
                            ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 结课评测（默认 decision=complete）。
     */
    public EvalResult evaluate(String topic, String cefr, List<CetTutorTurnRepository.TurnRow> turns,
                               ModelRouter.CallContext ctx) {
        return evaluate(topic, cefr, turns, null, "complete", Decision.COMPLETE, ctx);
    }

    /**
     * 阶段评测（默认 decision=continue）。
     */
    public EvalResult evaluateStage(String topic, String cefr, String planJson,
                                    List<CetTutorTurnRepository.TurnRow> turns,
                                    ModelRouter.CallContext ctx) {
        return evaluate(topic, cefr, turns, planJson, "stage", Decision.CONTINUE, ctx);
    }

    private EvalResult evaluate(String topic, String cefr, List<CetTutorTurnRepository.TurnRow> turns,
                                String planJson, String evalMode, Decision fallbackDecision,
                                ModelRouter.CallContext ctx) {
        String transcript = turns.stream()
                .map(t -> "Child: " + nullToEmpty(t.childText()) + "\nTutor: " + nullToEmpty(t.tutorText()))
                .collect(Collectors.joining("\n"));
        Map<String, String> vars = Map.of(
                "topic", nullToEmpty(topic),
                "cefr", nullToEmpty(cefr),
                "transcript", transcript.isEmpty() ? "(no turns)" : transcript,
                "planJson", nullToEmpty(planJson),
                "evalMode", evalMode
        );
        String system = promptTemplateService.loadAndRender("cet.eval.system", vars,
                "Output JSON assessment with decision continue|replan|complete.");
        String user = promptTemplateService.loadAndRender("cet.eval.user", vars,
                "Transcript:\n{{transcript}}");
        ModelRouter.CallContext evalCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_EVAL");
        try {
            String raw = modelRouter.callText(evalCtx, system, user);
            String json = SafetyGuard.extractJson(raw);
            return parseEvalResult(objectMapper, json, fallbackDecision);
        } catch (Exception e) {
            return fallbackResult(fallbackDecision);
        }
    }

    /**
     * 解析评测 JSON。
     *
     * @param mapper           mapper
     * @param assessmentJson   JSON
     * @param fallbackDecision 缺省决策
     * @return 结果
     */
    public static EvalResult parseEvalResult(ObjectMapper mapper, String assessmentJson,
                                             Decision fallbackDecision) throws Exception {
        JsonNode node = mapper.readTree(assessmentJson);
        String childSummary = node.path("childSummary").asText(
                node.path("encouragement").asText("Great job today!"));
        Decision decision = Decision.from(node.path("decision").asText(null), fallbackDecision);
        List<String> focus = new ArrayList<>();
        if (node.path("focus").isArray()) {
            for (JsonNode f : node.path("focus")) {
                if (StringUtils.hasText(f.asText())) {
                    focus.add(f.asText());
                }
            }
        }
        boolean pauseNewVocab = node.path("pauseNewVocab").asBoolean(false);
        String insertStage = node.path("insertStage").isMissingNode() || node.path("insertStage").isNull()
                ? null : node.path("insertStage").toString();
        ObjectNode normalized = (ObjectNode) node.deepCopy();
        normalized.put("decision", decision.name().toLowerCase(Locale.ROOT));
        return new EvalResult(normalized.toString(), childSummary, decision, focus, pauseNewVocab, insertStage);
    }

    private EvalResult fallbackResult(Decision decision) {
        ObjectNode fallback = objectMapper.createObjectNode();
        fallback.put("grammar", 70);
        fallback.put("vocabulary", 70);
        fallback.put("fluency", 70);
        fallback.putArray("problems");
        fallback.put("decision", decision.name().toLowerCase(Locale.ROOT));
        ArrayNode focus = fallback.putArray("focus");
        fallback.put("pauseNewVocab", false);
        fallback.putNull("insertStage");
        fallback.put("encouragement", "You did a great job practicing today!");
        fallback.put("childSummary", "今天练习很棒，下次继续加油！");
        List<String> focusList = new ArrayList<>();
        if (decision == Decision.REPLAN) {
            focusList.add("grammar:review");
            focus.add("grammar:review");
            fallback.put("pauseNewVocab", true);
            fallback.put("childSummary", "我们换一种更简单的方式继续练！");
        }
        return new EvalResult(fallback.toString(), fallback.path("childSummary").asText(),
                decision, List.copyOf(focusList), fallback.path("pauseNewVocab").asBoolean(false), null);
    }

    /**
     * 解析会话评测 JSON（单测用）。
     *
     * @author liudy
     */
    public static SessionScores parseScores(ObjectMapper mapper, String assessmentJson) throws Exception {
        JsonNode n = mapper.readTree(assessmentJson);
        return new SessionScores(
                n.path("grammar").asInt(0),
                n.path("vocabulary").asInt(0),
                n.path("fluency").asInt(0),
                n.path("encouragement").asText(""),
                n.path("childSummary").asText("")
        );
    }

    /**
     * 从计划 JSON 解析阶段目标轮次阈值（缺省 8）。
     *
     * @param mapper   mapper
     * @param planJson 计划
     * @return 阈值
     */
    public static int resolveTargetTurns(ObjectMapper mapper, String planJson) {
        try {
            JsonNode root = mapper.readTree(planJson);
            JsonNode objectives = root.path("objectives");
            if (objectives.isObject() && objectives.path("targetTurns").isNumber()) {
                return Math.max(3, objectives.path("targetTurns").asInt(8));
            }
            JsonNode stages = root.path("stages");
            if (stages.isArray() && !stages.isEmpty()) {
                int sum = 0;
                for (JsonNode s : stages) {
                    sum += Math.max(1, s.path("targetTurns").asInt(2));
                }
                return Math.max(3, sum);
            }
        } catch (Exception ignored) {
            // fallback
        }
        return 8;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * 评测结果。
     *
     * @param assessmentJson JSON
     * @param childSummary   儿童摘要
     * @param decision       决策
     * @param focus          焦点
     * @param pauseNewVocab  暂停新词
     * @param insertStageJson 插入阶段 JSON
     * @author liudy
     */
    public record EvalResult(String assessmentJson, String childSummary, Decision decision,
                             List<String> focus, boolean pauseNewVocab, String insertStageJson) {
        public EvalResult(String assessmentJson, String childSummary) {
            this(assessmentJson, childSummary, Decision.COMPLETE, List.of(), false, null);
        }
    }

    /**
     * 分数视图。
     *
     * @author liudy
     */
    public record SessionScores(int grammar, int vocabulary, int fluency, String encouragement, String childSummary) {
    }
}
