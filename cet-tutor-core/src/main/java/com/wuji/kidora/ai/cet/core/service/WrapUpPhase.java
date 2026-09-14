package com.wuji.kidora.ai.cet.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 结课告别阶段（存于 {@code cet_lesson_session.extra_json}）。
 *
 * @author liudy
 */
public final class WrapUpPhase {

    public static final String KEY_PHASE = "wrapUpPhase";
    public static final String KEY_PENDING_SUMMARY = "pendingChildSummary";

    public static final String PENDING_TUTOR_FAREWELL = "pending_tutor_farewell";
    public static final String AWAIT_CHILD_FAREWELL = "await_child_farewell";

    private WrapUpPhase() {
    }

    /**
     * 读取当前告别阶段，缺省为空。
     *
     * @param repository 会话仓储
     * @param sessionId  会话 id
     * @param mapper     JSON
     * @return phase 或 empty
     */
    public static Optional<String> readPhase(CetLessonSessionRepository repository,
                                             String sessionId,
                                             ObjectMapper mapper) {
        return readRoot(repository, sessionId, mapper).map(n -> n.path(KEY_PHASE).asText(null))
                .filter(StringUtils::hasText);
    }

    /**
     * 读取阶段评测缓存的儿童摘要。
     */
    public static Optional<String> readPendingSummary(CetLessonSessionRepository repository,
                                                      String sessionId,
                                                      ObjectMapper mapper) {
        return readRoot(repository, sessionId, mapper).map(n -> n.path(KEY_PENDING_SUMMARY).asText(null))
                .filter(StringUtils::hasText);
    }

    /**
     * 阶段评测 complete：待外教第 1 句再见。
     */
    public static void setPendingTutorFarewell(CetLessonSessionRepository repository,
                                               String sessionId,
                                               String pendingChildSummary,
                                               ObjectMapper mapper) {
        ObjectNode node = loadMerged(repository, sessionId, mapper);
        node.put(KEY_PHASE, PENDING_TUTOR_FAREWELL);
        if (StringUtils.hasText(pendingChildSummary)) {
            node.put(KEY_PENDING_SUMMARY, pendingChildSummary);
        }
        repository.updateExtraJson(sessionId, node.toString());
    }

    /**
     * 外教第 1 句已下发，等待孩子回再见。
     */
    public static void setAwaitChildFarewell(CetLessonSessionRepository repository,
                                             String sessionId,
                                             ObjectMapper mapper) {
        ObjectNode node = loadMerged(repository, sessionId, mapper);
        node.put(KEY_PHASE, AWAIT_CHILD_FAREWELL);
        repository.updateExtraJson(sessionId, node.toString());
    }

    /**
     * 清除告别相关字段（结课/中止后）。
     */
    public static void clear(CetLessonSessionRepository repository,
                             String sessionId,
                             ObjectMapper mapper) {
        ObjectNode node = loadMerged(repository, sessionId, mapper);
        node.remove(KEY_PHASE);
        node.remove(KEY_PENDING_SUMMARY);
        repository.updateExtraJson(sessionId, node.toString());
    }

    private static Optional<JsonNode> readRoot(CetLessonSessionRepository repository,
                                               String sessionId,
                                               ObjectMapper mapper) {
        return repository.findExtraJson(sessionId).flatMap(json -> {
            try {
                JsonNode n = mapper.readTree(json);
                return n.isObject() ? Optional.of(n) : Optional.empty();
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private static ObjectNode loadMerged(CetLessonSessionRepository repository,
                                         String sessionId,
                                         ObjectMapper mapper) {
        ObjectNode node = mapper.createObjectNode();
        Optional<String> existing = repository.findExtraJson(sessionId);
        if (existing.isPresent()) {
            try {
                JsonNode prev = mapper.readTree(existing.get());
                if (prev.isObject()) {
                    node = (ObjectNode) prev.deepCopy();
                }
            } catch (Exception ignored) {
                // fall through with empty object
            }
        }
        return node;
    }
}
