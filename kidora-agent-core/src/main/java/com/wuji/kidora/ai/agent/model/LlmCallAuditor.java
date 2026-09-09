package com.wuji.kidora.ai.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.common.util.PostgresText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 入模审计写入 llm_call_log。
 *
 * @author liudy
 */
@Component
public class LlmCallAuditor {

    private static final Logger log = LoggerFactory.getLogger(LlmCallAuditor.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public LlmCallAuditor(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void record(AuditParams params) {
        try {
            String requestJson = PostgresText.sanitizeJson(
                    objectMapper.writeValueAsString(sanitizeForJsonb(params.request())));
            String responseJson = params.response() == null
                    ? null
                    : PostgresText.sanitizeJson(objectMapper.writeValueAsString(sanitizeForJsonb(params.response())));
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            jdbcTemplate.update("""
                    INSERT INTO llm_call_log
                    (id, call_id, trace_id, session_id, message_id, user_id, learner_id, biz_source, biz_ref_id,
                     model_id, provider,
                     attempt, is_fallback, status, error_code, latency_ms, prompt_tokens, completion_tokens,
                     request_json, response_json, create_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """,
                    IdGenerator.nextLong(),
                    IdGenerator.nextBizId("call_"),
                    params.traceId(),
                    params.sessionId(),
                    params.messageId(),
                    params.userId(),
                    params.learnerId(),
                    params.bizSource(),
                    params.bizRefId(),
                    params.modelId(),
                    params.provider(),
                    params.attempt(),
                    params.fallback(),
                    params.status(),
                    params.errorCode(),
                    params.latencyMs(),
                    params.promptTokens(),
                    params.completionTokens(),
                    requestJson,
                    responseJson,
                    Timestamp.from(now.toInstant()));
        } catch (Exception e) {
            log.warn("write llm_call_log failed: {}", e.toString());
        }
    }

    static Object sanitizeForJsonb(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return PostgresText.sanitize(s);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), sanitizeForJsonb(e.getValue()));
            }
            return out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(sanitizeForJsonb(o));
            }
            return out;
        }
        return value;
    }

    public record AuditParams(
            String traceId,
            String sessionId,
            String messageId,
            String userId,
            String learnerId,
            String bizSource,
            String bizRefId,
            String modelId,
            String provider,
            int attempt,
            boolean fallback,
            String status,
            String errorCode,
            Integer latencyMs,
            Integer promptTokens,
            Integer completionTokens,
            Map<String, Object> request,
            Map<String, Object> response
    ) {
    }
}
