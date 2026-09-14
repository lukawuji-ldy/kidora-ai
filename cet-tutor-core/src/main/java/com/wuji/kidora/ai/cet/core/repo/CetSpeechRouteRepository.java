package com.wuji.kidora.ai.cet.core.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * speech_route 只读仓储（CET 侧解析人设音色用 primary_vendor）。
 *
 * @author liudy
 */
@Repository
public class CetSpeechRouteRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetSpeechRouteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 读取主供应商 code。
     *
     * @return primary_vendor；无行或空则 empty
     */
    public Optional<String> findPrimaryVendor() {
        List<String> rows = jdbcTemplate.query("""
                SELECT primary_vendor FROM speech_route WHERE id = 1
                """, (rs, i) -> rs.getString(1));
        if (rows.isEmpty() || !StringUtils.hasText(rows.get(0))) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).trim());
    }
}
