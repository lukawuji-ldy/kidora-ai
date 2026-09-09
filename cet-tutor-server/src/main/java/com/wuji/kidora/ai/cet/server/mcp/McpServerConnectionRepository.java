package com.wuji.kidora.ai.cet.server.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 读取 ACTIVE {@code mcp_server_ref}。
 *
 * @author liudy
 */
@Repository
@ConditionalOnProperty(prefix = "kidora.mcp", name = "enabled", havingValue = "true")
public class McpServerConnectionRepository {

    private static final RowMapper<McpServerConnection> ROW_MAPPER = (rs, i) -> new McpServerConnection(
            rs.getString("server_id"),
            rs.getString("name"),
            rs.getString("base_url"),
            rs.getString("auth_type"),
            rs.getString("status"));

    private final JdbcTemplate jdbcTemplate;

    public McpServerConnectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * ACTIVE 且未删除的 Server。
     *
     * @return 连接列表
     */
    public List<McpServerConnection> listActive() {
        return jdbcTemplate.query("""
                SELECT server_id, name, base_url, auth_type, status
                FROM mcp_server_ref
                WHERE status = 'ACTIVE' AND deleted = FALSE
                ORDER BY server_id ASC
                """, ROW_MAPPER);
    }
}
