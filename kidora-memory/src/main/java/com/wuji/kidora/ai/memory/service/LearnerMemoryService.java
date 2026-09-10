package com.wuji.kidora.ai.memory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.memory.port.LearnerProfilePort;
import com.wuji.kidora.ai.memory.port.MemoryWritePort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 结课画像合并：规则合并评测 JSON 到 extra_json，并写语义短事实。
 *
 * @author liudy
 */
@Service
public class LearnerMemoryService {

    private final LearnerProfilePort learnerProfilePort;
    private final MemoryWritePort memoryWritePort;
    private final ObjectMapper objectMapper;

    public LearnerMemoryService(LearnerProfilePort learnerProfilePort,
                                MemoryWritePort memoryWritePort,
                                ObjectMapper objectMapper) {
        this.learnerProfilePort = learnerProfilePort;
        this.memoryWritePort = memoryWritePort;
        this.objectMapper = objectMapper;
    }

    /**
     * 结课后更新画像与语义记忆。
     *
     * @param learnerId       学习者
     * @param sessionId       会话
     * @param topic           主题
     * @param assessmentJson  评测 JSON
     */
    public void onSessionCompleted(String learnerId, String sessionId, String topic, String assessmentJson) {
        if (!StringUtils.hasText(learnerId) || !StringUtils.hasText(assessmentJson)) {
            return;
        }
        try {
            String merged = mergeExtra(learnerId, assessmentJson, topic);
            learnerProfilePort.updateExtraJson(learnerId, merged);
            writeSemanticFacts(learnerId, sessionId, topic, assessmentJson);
        } catch (Exception ignored) {
            // best-effort：结课主路径不因记忆失败中断
        }
    }

    /**
     * 合并 extra_json（可单测）。
     *
     * @param existingExtra 已有
     * @param assessmentJson 评测
     * @param topic 主题
     * @return 合并后 JSON
     */
    public String mergeExtraJson(String existingExtra, String assessmentJson, String topic) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        if (StringUtils.hasText(existingExtra)) {
            JsonNode existing = objectMapper.readTree(existingExtra);
            if (existing.isObject()) {
                root = (ObjectNode) existing.deepCopy();
            }
        }
        JsonNode assess = objectMapper.readTree(assessmentJson);
        mergeStringArray(root, "commonGrammarErrors", assess.path("problems"));
        mergeStringArray(root, "commonGrammarErrors", assess.path("focus"));
        if (assess.path("pronunciation").isNumber() && assess.path("pronunciation").asDouble() < 70) {
            ArrayNode weak = root.withArray("pronunciationWeakness");
            addUnique(weak, "needs_practice");
        }
        if (StringUtils.hasText(topic)) {
            ArrayNode prefs = root.withArray("learningPreferences");
            addUnique(prefs, "topic:" + topic.trim());
            ArrayNode known = root.withArray("knownVocabulary");
            for (String token : topic.trim().split("\\s+")) {
                if (token.length() >= 2) {
                    addUnique(known, token.toLowerCase());
                }
            }
        }
        root.put("lastSessionTopic", topic == null ? "" : topic);
        return root.toString();
    }

    private String mergeExtra(String learnerId, String assessmentJson, String topic) throws Exception {
        String existing = learnerProfilePort.findByLearnerId(learnerId)
                .map(p -> p.extraJson())
                .orElse(null);
        return mergeExtraJson(existing, assessmentJson, topic);
    }

    private void writeSemanticFacts(String learnerId, String sessionId, String topic, String assessmentJson)
            throws Exception {
        JsonNode assess = objectMapper.readTree(assessmentJson);
        if (StringUtils.hasText(topic)) {
            memoryWritePort.writeSemantic(learnerId,
                    "Practiced topic: " + topic,
                    sessionId,
                    "{\"source\":\"session_complete\"}");
        }
        if (assess.path("problems").isArray()) {
            for (JsonNode p : assess.path("problems")) {
                if (StringUtils.hasText(p.asText())) {
                    memoryWritePort.writeSemantic(learnerId,
                            "Problem: " + p.asText(),
                            sessionId,
                            "{\"source\":\"eval_problem\"}");
                }
            }
        }
        String summary = assess.path("childSummary").asText(null);
        if (StringUtils.hasText(summary) && summary.length() <= 200) {
            memoryWritePort.writeSemantic(learnerId, summary, sessionId, "{\"source\":\"child_summary\"}");
        }
    }

    private void mergeStringArray(ObjectNode root, String field, JsonNode source) {
        if (source == null || source.isMissingNode() || source.isNull()) {
            return;
        }
        ArrayNode target = root.withArray(field);
        if (source.isArray()) {
            for (JsonNode n : source) {
                addUnique(target, n.asText());
            }
        } else if (source.isTextual()) {
            addUnique(target, source.asText());
        }
    }

    private static void addUnique(ArrayNode arr, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Set<String> existing = new LinkedHashSet<>();
        Iterator<JsonNode> it = arr.elements();
        while (it.hasNext()) {
            existing.add(it.next().asText());
        }
        if (existing.add(value.trim()) && existing.size() <= 40) {
            arr.removeAll();
            existing.forEach(arr::add);
        }
    }
}
