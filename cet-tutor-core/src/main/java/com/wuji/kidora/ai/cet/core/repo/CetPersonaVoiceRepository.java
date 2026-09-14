package com.wuji.kidora.ai.cet.core.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * CET 人设 ↔ 厂商 TTS 音色映射只读仓储。
 *
 * @author liudy
 */
@Repository
public class CetPersonaVoiceRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetPersonaVoiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询 ACTIVE 映射的 voice_id。
     *
     * @param personaId  人设 id
     * @param vendorCode 厂商 code（与 speech_route.primary 对齐）
     * @return voice_id；无行或参数空则 empty
     */
    public Optional<String> findActiveVoiceId(String personaId, String vendorCode) {
        if (!StringUtils.hasText(personaId) || !StringUtils.hasText(vendorCode)) {
            return Optional.empty();
        }
        List<String> rows = jdbcTemplate.query("""
                SELECT voice_id FROM cet_persona_voice
                WHERE persona_id = ? AND vendor_code = ? AND status = 'ACTIVE'
                """, (rs, i) -> rs.getString(1), personaId.trim(), vendorCode.trim());
        if (rows.isEmpty() || !StringUtils.hasText(rows.get(0))) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).trim());
    }
}
