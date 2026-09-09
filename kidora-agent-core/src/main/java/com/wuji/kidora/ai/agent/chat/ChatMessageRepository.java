package com.wuji.kidora.ai.agent.chat;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * chat_message JDBC 仓储。
 *
 * @author liudy
 */
@Repository
public class ChatMessageRepository {

    public record MessageRow(String messageId, String sessionId, String userId, String role,
                             String content, String status, Instant createTime) {
    }

    private static final RowMapper<MessageRow> MAPPER = (rs, rowNum) -> new MessageRow(
            rs.getString("message_id"),
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getString("status"),
            rs.getTimestamp("create_time").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String insert(String sessionId, String userId, String role, String content, String status) {
        String messageId = IdGenerator.nextBizId("msg_");
        jdbcTemplate.update("""
                INSERT INTO chat_message
                (id, message_id, session_id, user_id, role, content, token_count, status, create_time)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                IdGenerator.nextLong(),
                messageId,
                sessionId,
                userId,
                role,
                content == null ? "" : content,
                status,
                Timestamp.from(Instant.now()));
        return messageId;
    }

    public List<MessageRow> listBySession(String sessionId) {
        return jdbcTemplate.query("""
                SELECT message_id, session_id, user_id, role, content, status, create_time
                FROM chat_message
                WHERE session_id = ?
                ORDER BY create_time ASC
                """, MAPPER, sessionId);
    }

    /**
     * 最近 N 条（时间正序返回，便于拼上下文）。
     *
     * @author liudy
     */
    public List<MessageRow> listRecent(String sessionId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<MessageRow> desc = jdbcTemplate.query("""
                SELECT message_id, session_id, user_id, role, content, status, create_time
                FROM chat_message
                WHERE session_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """, MAPPER, sessionId, limit);
        List<MessageRow> asc = new ArrayList<>(desc);
        Collections.reverse(asc);
        return asc;
    }

    /**
     * 窗口裁剪纯函数（单测用）。
     *
     * @author liudy
     */
    public static List<MessageRow> trimWindow(List<MessageRow> all, int windowSize) {
        if (all == null || all.isEmpty() || windowSize <= 0) {
            return List.of();
        }
        if (all.size() <= windowSize) {
            return List.copyOf(all);
        }
        return List.copyOf(all.subList(all.size() - windowSize, all.size()));
    }
}
