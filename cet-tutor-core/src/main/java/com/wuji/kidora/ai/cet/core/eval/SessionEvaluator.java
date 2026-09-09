package com.wuji.kidora.ai.cet.core.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import com.wuji.kidora.ai.cet.core.safety.SafetyGuard;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话级评测摘要（MVP-1；不做 Re-plan）。
 *
 * @author liudy
 */
@Component
public class SessionEvaluator {

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

    public EvalResult evaluate(String topic, String cefr, List<CetTutorTurnRepository.TurnRow> turns,
                               ModelRouter.CallContext ctx) {
        String transcript = turns.stream()
                .map(t -> "Child: " + nullToEmpty(t.childText()) + "\nTutor: " + nullToEmpty(t.tutorText()))
                .collect(Collectors.joining("\n"));
        Map<String, String> vars = Map.of(
                "topic", nullToEmpty(topic),
                "cefr", nullToEmpty(cefr),
                "transcript", transcript.isEmpty() ? "(no turns)" : transcript
        );
        String system = promptTemplateService.loadAndRender("cet.eval.system", vars,
                "Output JSON assessment for child session.");
        String user = promptTemplateService.loadAndRender("cet.eval.user", vars,
                "Transcript:\n{{transcript}}");
        ModelRouter.CallContext evalCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_EVAL");
        try {
            String raw = modelRouter.callText(evalCtx, system, user);
            String json = SafetyGuard.extractJson(raw);
            JsonNode node = objectMapper.readTree(json);
            String childSummary = node.path("childSummary").asText(
                    node.path("encouragement").asText("Great job today!"));
            return new EvalResult(json, childSummary);
        } catch (Exception e) {
            ObjectNode fallback = objectMapper.createObjectNode();
            fallback.put("grammar", 70);
            fallback.put("vocabulary", 70);
            fallback.put("fluency", 70);
            fallback.put("encouragement", "You did a great job practicing today!");
            fallback.put("childSummary", "今天练习很棒，下次继续加油！");
            return new EvalResult(fallback.toString(), fallback.path("childSummary").asText());
        }
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public record EvalResult(String assessmentJson, String childSummary) {
    }

    public record SessionScores(int grammar, int vocabulary, int fluency, String encouragement, String childSummary) {
    }
}
