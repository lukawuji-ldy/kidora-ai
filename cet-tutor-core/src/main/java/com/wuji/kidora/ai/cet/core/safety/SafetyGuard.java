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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety L0 规则 + L1/L2 模型分类（模型不可用时 fail-closed：SOFT_BLOCK）。
 * O1：课堂短答入站快路径 / 短鼓励出站可跳过模型，降低听感路径双 Safety LLM 墙钟。
 *
 * @author liudy
 */
@Component
public class SafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SafetyGuard.class);

    static final int INPUT_FAST_MAX_LEN = 80;
    static final int OUTPUT_SKIP_MAX_LEN = 220;

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

    /** 入站允许字符：拉丁字母数字、常见英文标点、空格、有限中文标点。 */
    private static final Pattern INPUT_SAFE_CHARSET = Pattern.compile(
            "^[A-Za-z0-9\\s.,!?'\\-\"‘’“”…、。！？，]+$");

    private static final Pattern INPUT_YES_NO = Pattern.compile(
            "(?i)^(yes|no|yeah|yep|yup|ok|okay|nope)\\.?$");
    private static final Pattern INPUT_I_LIKE = Pattern.compile(
            "(?i)^i\\s+(like|love|have|see|want|am)\\b.+$");
    private static final Pattern INPUT_MY_IS = Pattern.compile(
            "(?i)^my\\s+.+\\s+(is|are)\\b.+$");
    private static final Pattern INPUT_IT_THIS = Pattern.compile(
            "(?i)^(it'?s|this\\s+is|that\\s+is)\\b.+$");

    private static final Set<String> CLASSROOM_WORDS = Set.of(
            "red", "blue", "green", "yellow", "orange", "purple", "pink", "black", "white", "brown",
            "dog", "dogs", "cat", "cats", "bird", "birds", "fish", "pet", "pets", "hamster",
            "apple", "banana", "food", "water", "school", "friend", "friends", "mom", "dad",
            "big", "small", "happy", "sad", "hello", "hi", "bye", "thanks", "thank"
    );

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://|www\\.");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?\\d[\\d\\s\\-]{7,}\\d)(?!\\d)");

    private static final Pattern OUT_ENCOURAGE = Pattern.compile(
            "(?i)(不错|很好|真棒|太棒|加油|试得很好|great|good\\s+try|nice|well\\s+done|amazing|good\\s+job)");
    private static final Pattern OUT_QUESTION = Pattern.compile(
            "[?？]|\\b(what|where|who|how|do\\s+you|can\\s+you|is\\s+your|are\\s+you)\\b|吗|呢");

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
        if (matchesInputFastAllow(text)) {
            return new SafetyDecision(SafetyAction.ALLOW, "L0_FAST_ALLOW", "classroom short answer", null);
        }
        return checkModel(text, "INPUT", ctx);
    }

    public SafetyDecision checkOutput(String text, ModelRouter.CallContext ctx) {
        SafetyDecision l0 = checkL0(text);
        if (!l0.action().equals(SafetyAction.ALLOW)) {
            return l0;
        }
        if (matchesOutputSkipModel(text)) {
            return new SafetyDecision(SafetyAction.ALLOW, "L0_OUT_SKIP_MODEL", "short encourage scaffold", null);
        }
        return checkModel(text, "OUTPUT", ctx);
    }

    /**
     * 仅 L0 HARD/SOFT（单测与内部复用）。
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

    /**
     * 入站课堂短答快路径（不打模型）。
     */
    boolean matchesInputFastAllow(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() > INPUT_FAST_MAX_LEN) {
            return false;
        }
        if (!INPUT_SAFE_CHARSET.matcher(trimmed).matches()) {
            return false;
        }
        if (checkL0(trimmed).action() != SafetyAction.ALLOW) {
            return false;
        }
        if (INPUT_YES_NO.matcher(trimmed).matches()) {
            return true;
        }
        if (INPUT_I_LIKE.matcher(trimmed).matches()
                || INPUT_MY_IS.matcher(trimmed).matches()
                || INPUT_IT_THIS.matcher(trimmed).matches()) {
            return true;
        }
        String[] words = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ")
                .trim().split("\\s+");
        if (words.length == 1 && CLASSROOM_WORDS.contains(words[0])) {
            return true;
        }
        // 纯英文短句：≤5 词且 ≤40 字符
        if (trimmed.length() <= 40 && words.length <= 5 && words.length >= 1
                && words[0].matches("[a-z]+")) {
            return true;
        }
        return false;
    }

    /**
     * 出站短鼓励脚手架：跳过模型。
     */
    boolean matchesOutputSkipModel(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() > OUTPUT_SKIP_MAX_LEN) {
            return false;
        }
        if (URL_PATTERN.matcher(trimmed).find()
                || EMAIL_PATTERN.matcher(trimmed).find()
                || PHONE_PATTERN.matcher(trimmed).find()) {
            return false;
        }
        if (checkL0(trimmed).action() != SafetyAction.ALLOW) {
            return false;
        }
        boolean encourage = OUT_ENCOURAGE.matcher(trimmed).find();
        boolean question = OUT_QUESTION.matcher(trimmed).find();
        boolean hasLatin = trimmed.chars().anyMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'));
        boolean hasCjk = trimmed.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
        // 鼓励 + 问句；或中英脚手架（含中文讲解 + 英文）且带问句
        if (encourage && question) {
            return true;
        }
        return hasCjk && hasLatin && question && trimmed.length() <= OUTPUT_SKIP_MAX_LEN;
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
