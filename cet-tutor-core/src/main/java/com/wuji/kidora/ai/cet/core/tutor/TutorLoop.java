package com.wuji.kidora.ai.cet.core.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tutor 小循环：禁止调用 Planner/Re-Planner。
 * 先完整生成全文，由上层做 L2 闸门后再按块 SSE。
 *
 * @author liudy
 */
@Component
public class TutorLoop {

    public static final int SSE_CHUNK_SIZE = 24;

    private final PromptTemplateService promptTemplateService;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public TutorLoop(PromptTemplateService promptTemplateService,
                     ModelRouter modelRouter,
                     ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * 阻塞生成 Tutor 全文（供输出闸门后再流式下发）。
     *
     * @author liudy
     */
    public String generateReply(String planJson,
                                String cefr,
                                String personaId,
                                String childText,
                                List<CetTutorTurnRepository.TurnRow> recentTurns,
                                ModelRouter.CallContext ctx) {
        String stageId = resolveStageId(planJson, recentTurns);
        String planSummary = summarizePlan(planJson);
        String recent = formatRecent(recentTurns);
        Map<String, String> vars = Map.of(
                "personaId", nullTo(personaId, "emma"),
                "stageId", stageId,
                "cefr", nullTo(cefr, "A1"),
                "planSummary", planSummary,
                "recentTurns", recent,
                "childText", childText == null ? "" : childText
        );
        String system = promptTemplateService.loadAndRender("cet.tutor.system", vars,
                "You are a friendly child English tutor. Stay on stage.");
        String user = promptTemplateService.loadAndRender("cet.tutor.user", vars,
                "Child says: {{childText}}");
        ModelRouter.CallContext tutorCtx = new ModelRouter.CallContext(
                ctx.traceId(), ctx.sessionId(), ctx.messageId(), ctx.userId(), ctx.learnerId(),
                "CET", ctx.bizRefId(), "CET_TUTOR");
        return modelRouter.callText(tutorCtx, system, user);
    }

    /**
     * 将闸门后的最终文本按固定块拆成 SSE delta。
     *
     * @author liudy
     */
    public static Flux<String> chunkForSse(String text) {
        if (!StringUtils.hasText(text)) {
            return Flux.empty();
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += SSE_CHUNK_SIZE) {
            chunks.add(text.substring(i, Math.min(i + SSE_CHUNK_SIZE, text.length())));
        }
        return Flux.fromIterable(chunks);
    }

    public String resolveStageId(String planJson, List<CetTutorTurnRepository.TurnRow> recentTurns) {
        int turnCount = recentTurns == null ? 0 : recentTurns.size();
        try {
            JsonNode stages = objectMapper.readTree(planJson).path("stages");
            if (stages.isArray() && !stages.isEmpty()) {
                int idx = Math.min(turnCount / 2, stages.size() - 1);
                return stages.get(idx).path("id").asText("dialog");
            }
        } catch (Exception ignored) {
            // fallback
        }
        return "dialog";
    }

    private String summarizePlan(String planJson) {
        try {
            JsonNode n = objectMapper.readTree(planJson);
            return "topic=" + n.path("topic").asText() + "; objectives=" + n.path("objectives");
        } catch (Exception e) {
            return StringUtils.hasText(planJson) ? planJson.substring(0, Math.min(200, planJson.length())) : "";
        }
    }

    private String formatRecent(List<CetTutorTurnRepository.TurnRow> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return "(none)";
        }
        return recentTurns.stream()
                .sorted(Comparator.comparingInt(CetTutorTurnRepository.TurnRow::turnIndex))
                .map(t -> "#" + t.turnIndex() + " child=" + nullTo(t.childText(), "")
                        + " tutor=" + nullTo(t.tutorText(), ""))
                .collect(Collectors.joining("\n"));
    }

    private static String nullTo(String s, String d) {
        return StringUtils.hasText(s) ? s : d;
    }
}
