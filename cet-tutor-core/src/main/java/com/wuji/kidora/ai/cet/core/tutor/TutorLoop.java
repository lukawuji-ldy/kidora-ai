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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /** 从 tutor 文本抽取问句 / Say: 跟读提示。 */
    private static final Pattern ASK_PATTERN = Pattern.compile(
            "(?:Say:\\s*[^?!.\\n]+|[^?!.\\n]+\\?)",
            Pattern.CASE_INSENSITIVE);

    /** 外教声明本轮要展示哪张道具图的结构化标记，展示前必须剥离。 */
    private static final Pattern PROP_DIRECTIVE_PATTERN = Pattern.compile(
            "\\[\\[\\s*PROP\\s*:\\s*([A-Za-z0-9_-]{0,64})\\s*]]",
            Pattern.CASE_INSENSITIVE);

    /** 标记里表示「本轮不展示图片」的值。 */
    private static final String PROP_DIRECTIVE_NONE = "none";

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
    public TutorReply generateReply(String planJson,
                                String cefr,
                                String personaId,
                                String displayName,
                                String childText,
                                List<CetTutorTurnRepository.TurnRow> recentTurns,
                                ModelRouter.CallContext ctx) {
        return generateReply(planJson, cefr, personaId, displayName, childText,
                recentTurns, List.of(), ctx);
    }

    /**
     * 生成陪练回复，并将当前可用道具注入提示词约束。
     *
     * @param planJson             当前计划
     * @param cefr                 CEFR
     * @param personaId            人设
     * @param displayName          儿童昵称
     * @param childText            儿童输入
     * @param recentTurns         近期轮次
     * @param availablePropLemmas 当前已发布且可读的道具 lemma
     * @param ctx                  模型调用上下文
     * @return Tutor 全文与本轮道具声明
     * @author liudy
     */
    public TutorReply generateReply(String planJson,
                                String cefr,
                                String personaId,
                                String displayName,
                                String childText,
                                List<CetTutorTurnRepository.TurnRow> recentTurns,
                                List<String> availablePropLemmas,
                                ModelRouter.CallContext ctx) {
        String stageId = resolveStageId(planJson, recentTurns);
        String planSummary = summarizePlan(planJson);
        String stageGoal = resolveStageGoal(planJson, stageId);
        String recent = formatRecent(recentTurns);
        String recentAsks = extractRecentAsks(recentTurns);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("personaId", nullTo(personaId, "emma"));
        vars.put("displayName", nullTo(displayName, ""));
        vars.put("stageId", stageId);
        vars.put("stageGoal", stageGoal);
        vars.put("cefr", nullTo(cefr, "A1"));
        vars.put("planSummary", planSummary);
        vars.put("recentTurns", recent);
        vars.put("recentAsks", recentAsks);
        vars.put("childText", childText == null ? "" : childText);
        putPropVars(vars, availablePropLemmas);
        String system = promptTemplateService.loadAndRender("cet.tutor.system", vars,
                "You are a friendly child English tutor. Stay on stage. Address the child as {{displayName}} when natural. {{propInstruction}}");
        system = enforcePropInstruction(system, vars);
        String user = promptTemplateService.loadAndRender("cet.tutor.user", vars,
                "Child says: {{childText}}");
        return parseReply(callTutor(ctx, system, user));
    }

    /**
     * 开场：打招呼 + 点题 + 一个简单问题（无儿童输入）。
     *
     * @param planJson     当前计划
     * @param cefr         水平
     * @param personaId    人设
     * @param displayName  儿童昵称
     * @param topic        主题
     * @param ctx          调用上下文
     * @return 开场全文
     * @author liudy
     */
    public TutorReply generateOpening(String planJson,
                                  String cefr,
                                  String personaId,
                                  String displayName,
                                  String topic,
                                  ModelRouter.CallContext ctx) {
        return generateOpening(planJson, cefr, personaId, displayName, topic, List.of(), ctx);
    }

    /**
     * 生成开场，并根据已发布道具约束是否允许图片指认问题。
     *
     * @param planJson             当前计划
     * @param cefr                 CEFR
     * @param personaId            人设
     * @param displayName          儿童昵称
     * @param topic                主题
     * @param availablePropLemmas 当前已发布且可读的道具 lemma
     * @param ctx                  模型调用上下文
     * @return 开场全文与本轮道具声明
     * @author liudy
     */
    public TutorReply generateOpening(String planJson,
                                  String cefr,
                                  String personaId,
                                  String displayName,
                                  String topic,
                                  List<String> availablePropLemmas,
                                  ModelRouter.CallContext ctx) {
        String planSummary = summarizePlan(planJson);
        String stageGoal = resolveStageGoal(planJson, "warmup");
        String resolvedPersonaId = nullTo(personaId, "emma");
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("personaId", resolvedPersonaId);
        vars.put("personaName", personaDisplayName(personaId));
        vars.put("displayName", nullTo(displayName, ""));
        vars.put("dayGreeting", dayGreetingNow());
        vars.put("cefr", nullTo(cefr, "A1"));
        vars.put("planSummary", planSummary);
        vars.put("stageGoal", stageGoal);
        vars.put("topic", nullTo(topic, ""));
        putPropVars(vars, availablePropLemmas);
        String system = promptTemplateService.loadAndRender("cet.tutor.opening.system", vars,
                "You are a friendly child English tutor. Greet {{displayName}} briefly and ask one simple question. {{propInstruction}}");
        system = enforcePropInstruction(system, vars);
        String user = promptTemplateService.loadAndRender("cet.tutor.opening.user", vars,
                "Topic: {{topic}}. Plan: {{planSummary}}. Please greet and ask one question.");
        return parseReply(callTutor(ctx, system, user));
    }

    /**
     * 拆分外教原文与 {@code [[PROP:lemma]]} 声明，并把标记从文本里剥掉。
     *
     * <p>标记只是后端与模型之间的协议，必须在输出闸门、落库、SSE、TTS 之前消失。
     * 模型漏写或写成 {@code none} 时 {@code propLemma} 为 null，判定退回点名匹配。</p>
     *
     * @param raw 模型原始输出
     * @return 文本 + 道具声明
     * @author liudy
     */
    public static TutorReply parseReply(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new TutorReply(raw, null);
        }
        Matcher m = PROP_DIRECTIVE_PATTERN.matcher(raw);
        String lemma = null;
        while (m.find()) {
            String candidate = m.group(1);
            if (StringUtils.hasText(candidate)) {
                lemma = candidate.toLowerCase(Locale.ROOT);
            }
        }
        String text = m.replaceAll("")
                .replaceAll("[ \\t]*\\n[ \\t]*\\n[ \\t]*(\\n[ \\t]*)+", "\n\n")
                .trim();
        if (PROP_DIRECTIVE_NONE.equals(lemma)) {
            lemma = null;
        }
        return new TutorReply(text, lemma);
    }

    /**
     * 外教一轮输出。
     *
     * @param text      去掉道具标记后的外教全文
     * @param propLemma 外教声明要展示的道具 lemma；未声明为 null
     * @author liudy
     */
    public record TutorReply(String text, String propLemma) {
    }

    private static String enforcePropInstruction(String system, Map<String, String> vars) {
        String instruction = vars.get("propInstruction");
        if (!StringUtils.hasText(instruction)) {
            return system;
        }
        return system + "\n\n【动态道具约束（必须优先遵守）】\n" + instruction;
    }

    private static void putPropVars(Map<String, String> vars, List<String> availablePropLemmas) {
        List<String> props = availablePropLemmas == null
                ? List.of()
                : availablePropLemmas.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        vars.put("hasPropAssets", Boolean.toString(!props.isEmpty()));
        vars.put("availablePropLemmas", props.isEmpty() ? "(none)" : String.join(", ", props));
        vars.put("propInstruction", props.isEmpty()
                ? "当前没有可用道具图片。禁止提出 Look、What is this、指向图片、根据图片辨认颜色或大小等依赖图片的问题；"
                + "改用无图口语、跟读、选择或个人 WH 问题。回复末尾必须另起一行只写 [[PROP:none]]。"
                : "当前可用道具图片对应词为：" + String.join(", ", props)
                + "。只有点名这些词时才可以提出图片指认问题，且必须在句子里说出该英文词本身"
                + "（例如 Look at the dog! Is it big?），否则孩子屏幕上不会出现任何图片。"
                + "回复末尾必须另起一行，用 [[PROP:词]] 声明本轮孩子屏幕上要显示哪一张图，"
                + "词只能取上面列出的其中一个，且必须是本轮问题真正指向的那一个"
                + "（问 Is this a cat or a dog? 时选 cat 或 dog，不要选 pet 这种泛称）；"
                + "本轮不需要图片就写 [[PROP:none]]。这一行不会被孩子看到或听到。");
    }

    /**
     * 结课告别话术（step 1 外教先说再见；step 2 回应孩子并收束）。
     *
     * @param step          1 或 2
     * @param planJson      计划
     * @param cefr          级别
     * @param personaId     人设
     * @param displayName   儿童昵称
     * @param topic         主题
     * @param childFarewell 孩子告别语（step2）
     * @param recentTurns   近期轮次
     * @param ctx           上下文
     * @return 外教全文
     * @author liudy
     */
    public String generateWrapUpReply(int step,
                                      String planJson,
                                      String cefr,
                                      String personaId,
                                      String displayName,
                                      String topic,
                                      String childFarewell,
                                      List<CetTutorTurnRepository.TurnRow> recentTurns,
                                      ModelRouter.CallContext ctx) {
        String planSummary = summarizePlan(planJson);
        String recent = formatRecent(recentTurns);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("wrapUpStep", String.valueOf(step));
        vars.put("personaId", nullTo(personaId, "emma"));
        vars.put("personaName", personaDisplayName(personaId));
        vars.put("displayName", nullTo(displayName, ""));
        vars.put("cefr", nullTo(cefr, "A1"));
        vars.put("planSummary", planSummary);
        vars.put("topic", nullTo(topic, ""));
        vars.put("childFarewell", nullTo(childFarewell, ""));
        vars.put("recentTurns", recent);
        String system = promptTemplateService.loadAndRender("cet.tutor.wrapup.system", vars,
                "You are a friendly child English tutor saying goodbye. Step {{wrapUpStep}}. No new practice questions.");
        String user = promptTemplateService.loadAndRender("cet.tutor.wrapup.user", vars,
                "Step {{wrapUpStep}}. Topic {{topic}}. Child farewell: {{childFarewell}}.");
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

    /**
     * 计划摘要：topic / objectives / vocab / patterns / childGoals。
     *
     * @param planJson 计划 JSON
     * @return 注入 Prompt 的摘要串
     * @author liudy
     */
    String summarizePlan(String planJson) {
        try {
            JsonNode n = objectMapper.readTree(planJson);
            StringBuilder sb = new StringBuilder();
            sb.append("topic=").append(n.path("topic").asText());
            sb.append("; objectives=").append(n.path("objectives"));
            JsonNode objectives = n.path("objectives");
            if (objectives.isObject()) {
                if (objectives.path("vocabulary").isArray() && !objectives.path("vocabulary").isEmpty()) {
                    sb.append("; vocabulary=").append(objectives.path("vocabulary"));
                }
                if (objectives.path("patterns").isArray() && !objectives.path("patterns").isEmpty()) {
                    sb.append("; patterns=").append(objectives.path("patterns"));
                }
            }
            JsonNode childGoals = n.path("childGoals");
            if (childGoals.isArray() && !childGoals.isEmpty()) {
                sb.append("; childGoals=").append(childGoals);
            }
            return sb.toString();
        } catch (Exception e) {
            return StringUtils.hasText(planJson) ? planJson.substring(0, Math.min(200, planJson.length())) : "";
        }
    }

    /**
     * 解析当前阶段 goal；找不到则返回空串。
     *
     * @param planJson 计划 JSON
     * @param stageId  阶段 id
     * @return stage.goal 或空
     * @author liudy
     */
    String resolveStageGoal(String planJson, String stageId) {
        if (!StringUtils.hasText(planJson) || !StringUtils.hasText(stageId)) {
            return "";
        }
        try {
            JsonNode stages = objectMapper.readTree(planJson).path("stages");
            if (!stages.isArray()) {
                return "";
            }
            for (JsonNode stage : stages) {
                if (stageId.equals(stage.path("id").asText())) {
                    return stage.path("goal").asText("");
                }
            }
        } catch (Exception ignored) {
            // fallback empty
        }
        return "";
    }

    /**
     * 从近期 tutor 文本抽取已问句，供反重复。
     *
     * @param recentTurns 近期轮次
     * @return 多行已问句，或 (none)
     * @author liudy
     */
    static String extractRecentAsks(List<CetTutorTurnRepository.TurnRow> recentTurns) {
        if (recentTurns == null || recentTurns.isEmpty()) {
            return "(none)";
        }
        List<String> asks = new ArrayList<>();
        recentTurns.stream()
                .sorted(Comparator.comparingInt(CetTutorTurnRepository.TurnRow::turnIndex))
                .forEach(t -> asks.addAll(extractAsksFromTutorText(t.tutorText())));
        if (asks.isEmpty()) {
            return "(none)";
        }
        return String.join("\n", asks);
    }

    /**
     * 从单条 tutor 文本抽取问句或 Say: 跟读提示。
     *
     * @param tutorText 外教全文
     * @return 问句列表（已 trim）
     * @author liudy
     */
    static List<String> extractAsksFromTutorText(String tutorText) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(tutorText)) {
            return out;
        }
        String stripped = tutorText.replaceAll("（[^）]*）", "").replaceAll("\\([^)]*\\)", "");
        Matcher m = ASK_PATTERN.matcher(stripped);
        while (m.find()) {
            String ask = m.group().trim();
            if (StringUtils.hasText(ask)) {
                out.add(ask);
            }
        }
        return out;
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
