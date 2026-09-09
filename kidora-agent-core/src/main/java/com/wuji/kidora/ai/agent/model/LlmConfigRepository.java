package com.wuji.kidora.ai.agent.model;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.List;

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
}
