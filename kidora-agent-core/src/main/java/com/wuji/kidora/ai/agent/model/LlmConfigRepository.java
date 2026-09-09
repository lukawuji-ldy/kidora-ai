package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * llm_config 仓储。
 *
 * @author liudy
 */
@Repository
public class LlmConfigRepository {

    private static final RowMapper<LlmConfigRecord> MAPPER = (rs, rowNum) -> {
        LlmConfigRecord r = new LlmConfigRecord();
        r.setId(rs.getLong("id"));
        r.setConfigId(rs.getString("config_id"));
        r.setName(rs.getString("name"));
        r.setProvider(rs.getString("provider"));
        r.setModelKind(rs.getString("model_kind"));
        r.setBaseUrl(rs.getString("base_url"));
        r.setApiKeyCipher(rs.getString("api_key_cipher"));
        r.setModel(rs.getString("model"));
        r.setTemperature(rs.getBigDecimal("temperature"));
        int maxTokens = rs.getInt("max_tokens");
        r.setMaxTokens(rs.wasNull() ? null : maxTokens);
        Object extra = rs.getObject("extra_json");
        r.setExtraJson(extra == null ? null : extra.toString());
        r.setStatus(rs.getString("status"));
        Timestamp ct = rs.getTimestamp("create_time");
        Timestamp ut = rs.getTimestamp("update_time");
        if (ct != null) {
            r.setCreateTime(ct.toInstant().atOffset(ZoneOffset.UTC));
        }
        if (ut != null) {
            r.setUpdateTime(ut.toInstant().atOffset(ZoneOffset.UTC));
        }
        return r;
    };

    private final JdbcTemplate jdbcTemplate;

    public LlmConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LlmConfigRecord requireActive(String configId) {
        List<LlmConfigRecord> list = jdbcTemplate.query("""
                SELECT * FROM llm_config WHERE config_id = ? AND status = 'ACTIVE'
                """, MAPPER, configId);
        return list.stream().findFirst()
                .orElseThrow(() -> new KidoraException(ErrorCode.MODEL_UNAVAILABLE, "未找到可用 LLM 配置: " + configId));
    }

    public LlmConfigRecord requireActive(String configId, String modelKind) {
        LlmConfigRecord cfg = requireActive(configId);
        if (modelKind != null && !modelKind.equalsIgnoreCase(cfg.getModelKind())) {
            throw new KidoraException(ErrorCode.MODEL_UNAVAILABLE,
                    "LLM 配置 kind 不匹配: " + configId + " expect=" + modelKind);
        }
        return cfg;
    }

    public Optional<LlmConfigRecord> findByConfigId(String configId) {
        List<LlmConfigRecord> list = jdbcTemplate.query(
                "SELECT * FROM llm_config WHERE config_id = ?", MAPPER, configId);
        return list.stream().findFirst();
    }

    public List<LlmConfigRecord> list(String modelKind, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM llm_config WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(modelKind)) {
            sql.append(" AND model_kind = ?");
            args.add(modelKind);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY create_time ASC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long count(String modelKind, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM llm_config WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(modelKind)) {
            sql.append(" AND model_kind = ?");
            args.add(modelKind);
        }
        if (StringUtils.hasText(status)) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return total == null ? 0L : total;
    }

    public void insert(LlmConfigRecord record) {
        Timestamp now = Timestamp.from(Instant.now());
        long id = record.getId() != null ? record.getId() : IdGenerator.nextLong();
        jdbcTemplate.update("""
                INSERT INTO llm_config
                (id, config_id, name, provider, model_kind, base_url, api_key_cipher, model,
                 temperature, max_tokens, extra_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
                """,
                id,
                record.getConfigId(),
                record.getName(),
                record.getProvider(),
                record.getModelKind(),
                record.getBaseUrl(),
                record.getApiKeyCipher(),
                record.getModel(),
                record.getTemperature(),
                record.getMaxTokens(),
                blankToEmptyJson(record.getExtraJson()),
                record.getStatus(),
                now,
                now);
        record.setId(id);
    }

    public void update(LlmConfigRecord record, boolean updateApiKey) {
        Timestamp now = Timestamp.from(Instant.now());
        if (updateApiKey) {
            jdbcTemplate.update("""
                    UPDATE llm_config SET
                      name = ?, provider = ?, model_kind = ?, base_url = ?, api_key_cipher = ?,
                      model = ?, temperature = ?, max_tokens = ?, extra_json = CAST(? AS jsonb),
                      status = ?, update_time = ?
                    WHERE config_id = ?
                    """,
                    record.getName(),
                    record.getProvider(),
                    record.getModelKind(),
                    record.getBaseUrl(),
                    record.getApiKeyCipher(),
                    record.getModel(),
                    record.getTemperature(),
                    record.getMaxTokens(),
                    blankToEmptyJson(record.getExtraJson()),
                    record.getStatus(),
                    now,
                    record.getConfigId());
        } else {
            jdbcTemplate.update("""
                    UPDATE llm_config SET
                      name = ?, provider = ?, model_kind = ?, base_url = ?,
                      model = ?, temperature = ?, max_tokens = ?, extra_json = CAST(? AS jsonb),
                      status = ?, update_time = ?
                    WHERE config_id = ?
                    """,
                    record.getName(),
                    record.getProvider(),
                    record.getModelKind(),
                    record.getBaseUrl(),
                    record.getModel(),
                    record.getTemperature(),
                    record.getMaxTokens(),
                    blankToEmptyJson(record.getExtraJson()),
                    record.getStatus(),
                    now,
                    record.getConfigId());
        }
    }

    public int softDisable(String configId) {
        return jdbcTemplate.update("""
                UPDATE llm_config SET status = 'DISABLED', update_time = ? WHERE config_id = ?
                """, Timestamp.from(Instant.now()), configId);
    }

    private static String blankToEmptyJson(String extraJson) {
        if (!StringUtils.hasText(extraJson)) {
            return "{}";
        }
        return extraJson;
    }
}
