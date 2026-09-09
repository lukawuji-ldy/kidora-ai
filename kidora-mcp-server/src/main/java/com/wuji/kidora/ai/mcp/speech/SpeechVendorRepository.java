package com.wuji.kidora.ai.mcp.speech;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 读 speech_vendor_config / speech_route。
 *
 * @author liudy
 */
@Repository
@ConditionalOnProperty(prefix = "kidora.speech", name = "mode", havingValue = "db")
public class SpeechVendorRepository {

    private static final RowMapper<SpeechVendorRecord> VENDOR_MAPPER = (rs, i) -> new SpeechVendorRecord(
            rs.getString("vendor_code"),
            rs.getString("name"),
            rs.getString("status"),
            rs.getString("credentials_cipher"),
            rs.getString("extra_json"));

    private static final RowMapper<SpeechRouteRecord> ROUTE_MAPPER = (rs, i) -> new SpeechRouteRecord(
            rs.getString("primary_vendor"),
            rs.getString("backup_vendor"),
            rs.getString("tertiary_vendor"));

    private final JdbcTemplate jdbcTemplate;

    public SpeechVendorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按码读取供应商。
     *
     * @param vendorCode 码
     * @return 行
     */
    public Optional<SpeechVendorRecord> findByCode(String vendorCode) {
        var rows = jdbcTemplate.query("""
                SELECT vendor_code, name, status, credentials_cipher, extra_json::text AS extra_json
                FROM speech_vendor_config
                WHERE vendor_code = ?
                """, VENDOR_MAPPER, vendorCode);
        return rows.stream().findFirst();
    }

    /**
     * 读取主备路由（id=1）。
     *
     * @return 路由
     */
    public Optional<SpeechRouteRecord> findRoute() {
        var rows = jdbcTemplate.query("""
                SELECT primary_vendor, backup_vendor, tertiary_vendor
                FROM speech_route
                WHERE id = 1
                """, ROUTE_MAPPER);
        return rows.stream().findFirst();
    }
}
