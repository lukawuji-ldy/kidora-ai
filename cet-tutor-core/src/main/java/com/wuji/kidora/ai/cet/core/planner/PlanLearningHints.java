package com.wuji.kidora.ai.cet.core.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 从课时 plan_json 提取儿童目标与词提示（供 API / 前端道具）。
 *
 * @author liudy
 */
public final class PlanLearningHints {

    private static final int MAX_GOALS = 3;
    private static final int MAX_VOCAB = 5;
    private static final int MAX_PROP_CANDIDATES = 8;
    private static final Set<String> PROP_STOP_WORDS = Set.of(
            "a", "an", "and", "are", "be", "big", "color", "cute", "do",
            "does", "for", "hello", "how", "i", "is", "it", "like", "my",
            "of", "or", "please", "say", "small", "that", "the", "this",
            "to", "what", "which", "your");

    private PlanLearningHints() {
    }

    /**
     * 解析或合成恰好最多 3 条递进儿童目标。
     *
     * @param root  plan 根节点
     * @param topic 主题（fallback）
     * @return 目标列表（长度 1～3）
     */
    public static List<String> childGoals(JsonNode root, String topic) {
        List<String> fromPlan = readStringArray(root.path("childGoals"), MAX_GOALS);
        if (fromPlan.size() >= MAX_GOALS) {
            return fromPlan.subList(0, MAX_GOALS);
        }
        List<String> synthesized = synthesizeGoals(root, topic);
        if (fromPlan.isEmpty()) {
            return synthesized;
        }
        List<String> merged = new ArrayList<>(fromPlan);
        for (String s : synthesized) {
            if (merged.size() >= MAX_GOALS) {
                break;
            }
            if (!merged.contains(s)) {
                merged.add(s);
            }
        }
        while (merged.size() < MAX_GOALS) {
            merged.add(synthesized.get(Math.min(merged.size(), synthesized.size() - 1)));
        }
        return merged.subList(0, MAX_GOALS);
    }

    /**
     * 提取词提示（最多 5）。
     *
     * @param root plan 根节点
     * @return 词列表
     */
    public static List<String> vocabHints(JsonNode root) {
        JsonNode objectives = root.path("objectives");
        if (objectives.isObject()) {
            List<String> vocab = readStringArray(objectives.path("vocabulary"), MAX_VOCAB);
            if (!vocab.isEmpty()) {
                return vocab;
            }
            List<String> patterns = readStringArray(objectives.path("patterns"), MAX_VOCAB);
            if (!patterns.isEmpty()) {
                return patterns;
            }
        }
        if (objectives.isArray()) {
            List<String> fromArray = readStringArray(objectives, MAX_VOCAB);
            if (!fromArray.isEmpty()) {
                return fromArray;
            }
        }
        String topic = root.path("topic").asText("").trim();
        if (StringUtils.hasText(topic)) {
            return List.of(topic);
        }
        return List.of();
    }

    /**
     * 教学胶水词与寒暄词判定，道具解析与缺失入队共用同一份停用词。
     *
     * @param token 已小写的单词
     * @return true 表示不应作为道具候选
     */
    public static boolean isPropStopWord(String token) {
        return token != null && PROP_STOP_WORDS.contains(token);
    }

    /**
     * 提取需要检查道具库的候选词，过滤问句、寒暄和常见教学胶水词。
     *
     * <p>开课、续课详情与每轮道具可用性判定统一走这一条口径，避免同一会话在不同入口
     * 拿到不同的 propAssets。</p>
     *
     * @param root  plan 根
     * @param topic 会话主题
     * @return 候选词（保持出现顺序，最多 8 个）
     */
    public static List<String> propCandidateHints(JsonNode root, String topic) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        List<String> sources = new ArrayList<>();
        String sessionTopic = StringUtils.hasText(topic) ? topic.trim() : "";
        if (StringUtils.hasText(sessionTopic)) {
            sources.add(sessionTopic);
        }
        if (root != null && StringUtils.hasText(root.path("topic").asText(""))) {
            sources.add(root.path("topic").asText("").trim());
        }
        if (root != null) {
            sources.addAll(vocabHints(root));
        }
        for (String source : sources) {
            for (String token : source.toLowerCase(Locale.ROOT)
                    .split("[^a-zA-Z\\u4e00-\\u9fff]+")) {
                String candidate = token.trim();
                if (!StringUtils.hasText(candidate)
                        || PROP_STOP_WORDS.contains(candidate)
                        || candidate.length() > 64) {
                    continue;
                }
                out.add(candidate);
                if (out.size() >= MAX_PROP_CANDIDATES) {
                    return List.copyOf(out);
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * 根据会话主题选择生成任务主题目录。
     *
     * @param root  plan 根
     * @param topic 会话主题
     * @return pets、colors、food 或 default
     */
    public static String propTheme(JsonNode root, String topic) {
        String value = StringUtils.hasText(topic)
                ? topic.trim()
                : (root == null ? "" : root.path("topic").asText(""));
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("pet") || lower.contains("animal")
                || value.contains("宠物") || value.contains("动物")) {
            return "pets";
        }
        if (lower.contains("color") || lower.contains("colour") || value.contains("颜色")) {
            return "colors";
        }
        if (lower.contains("food") || lower.contains("fruit")
                || value.contains("食物") || value.contains("水果")) {
            return "food";
        }
        return "default";
    }

    /**
     * 确保 plan JSON 含满 3 条 childGoals（就地写入 ObjectNode）。
     *
     * @param root  可变根
     * @param topic 主题
     */
    public static void ensureChildGoals(ObjectNode root, String topic) {
        List<String> goals = childGoals(root, topic);
        ArrayNode arr = root.putArray("childGoals");
        for (String g : goals) {
            arr.add(g);
        }
    }

    private static List<String> synthesizeGoals(JsonNode root, String topic) {
        String t = StringUtils.hasText(topic) ? topic.trim() : root.path("topic").asText("今天的主题");
        List<String> vocab = vocabHints(root);
        String word = vocab.isEmpty() ? "关键词" : vocab.get(0);
        String pattern = "I can say it";
        JsonNode objectives = root.path("objectives");
        if (objectives.isObject()) {
            List<String> patterns = readStringArray(objectives.path("patterns"), 1);
            if (!patterns.isEmpty()) {
                pattern = patterns.get(0);
            }
        }
        return List.of(
                "认识「" + word + "」等词（主题：" + t + "）",
                "会说：「" + pattern + "」",
                "用起来：就「" + t + "」简单问答"
        );
    }

    private static List<String> readStringArray(JsonNode node, int max) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode n : node) {
            if (out.size() >= max) {
                break;
            }
            String s = n.asText("").trim();
            if (StringUtils.hasText(s)) {
                out.add(s);
            }
        }
        return out;
    }
}
