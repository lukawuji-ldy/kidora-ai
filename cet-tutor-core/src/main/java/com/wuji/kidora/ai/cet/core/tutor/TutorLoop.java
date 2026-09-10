package com.wuji.kidora.ai.cet.core.tutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.agent.model.ModelRouter;
import com.wuji.kidora.ai.agent.prompt.PromptTemplateService;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    /** 开场时段问候使用的时区（北京时间）。 */
    public static final ZoneId OPENING_ZONE = ZoneId.of("Asia/Shanghai");

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
        return callTutor(ctx, system, user);
    }

    /**
     * 开场：打招呼 + 点题 + 一个简单问题（无儿童输入）。
     *
     * @param planJson  当前计划
     * @param cefr      水平
     * @param personaId 人设
     * @param topic     主题
     * @param ctx       调用上下文
     * @return 开场全文
     * @author liudy
     */
    public String generateOpening(String planJson,
                                  String cefr,
                                  String personaId,
                                  String topic,
                                  ModelRouter.CallContext ctx) {
        String planSummary = summarizePlan(planJson);
        String resolvedPersonaId = nullTo(personaId, "emma");
        Map<String, String> vars = Map.of(
                "personaId", resolvedPersonaId,
                "personaName", personaDisplayName(personaId),
                "dayGreeting", dayGreetingNow(),
                "cefr", nullTo(cefr, "A1"),
                "planSummary", planSummary,
                "topic", nullTo(topic, "")
        );
        String system = promptTemplateService.loadAndRender("cet.tutor.opening.system", vars,
                "You are a friendly child English tutor. Greet briefly and ask one simple question.");
        String user = promptTemplateService.loadAndRender("cet.tutor.opening.user", vars,
                "Topic: {{topic}}. Plan: {{planSummary}}. Please greet and ask one question.");
        return callTutor(ctx, system, user);
    }

    /**
     * 按上海时区当前小时生成英文时段问候。
     *
     * @return Good morning / Good afternoon / Good evening
     * @author liudy
     */
    static String dayGreetingNow() {
        return dayGreetingForHour(ZonedDateTime.now(OPENING_ZONE).getHour());
    }

    /**
     * 按时段小时映射英文问候（便于单测，避免 flaky 时钟）。
     *
     * @param hour 0–23
     * @return Good morning / Good afternoon / Good evening
     * @author liudy
     */
    static String dayGreetingForHour(int hour) {
        if (hour >= 0 && hour < 12) {
            return "Good morning";
        }
        if (hour >= 12 && hour < 18) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    /**
     * 人设 id → 开场自我介绍用展示名。
     *
     * @param personaId 人设 id，可空
     * @return 稳定英文展示名
     * @author liudy
     */
    static String personaDisplayName(String personaId) {
        if (!StringUtils.hasText(personaId)) {
            return "Emma";
        }
        String id = personaId.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "emma" -> "Emma";
            case "mike" -> "Mike";
            case "lily" -> "Lily";
            case "tom" -> "Tom";
            case "coco" -> "Coco";
            case "alex" -> "Alex";
            default -> Character.toUpperCase(id.charAt(0)) + id.substring(1);
        };
    }

    private String callTutor(ModelRouter.CallContext ctx, String system, String user) {
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
