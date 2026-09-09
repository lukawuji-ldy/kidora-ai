package com.wuji.kidora.ai.agent.chat;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * chat_session JDBC 仓储。
 *
 * @author liudy
 */
@Repository
public class ChatSessionRepository {

    public record SessionRow(String sessionId, String userId, String title, int messageCount, Instant lastActiveTime) {
    }

    private static final RowMapper<SessionRow> MAPPER = (rs, rowNum) -> new SessionRow(
            rs.getString("session_id"),
            rs.getString("user_id"),
            rs.getString("title"),
            rs.getInt("message_count"),
            rs.getTimestamp("last_active_time").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String insert(String userId, String title) {
        String sessionId = IdGenerator.nextBizId("chs_");
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO chat_session
                (id, session_id, user_id, title, message_count, last_active_time, deleted, create_time)
                VALUES (?, ?, ?, ?, 0, ?, FALSE, ?)
                """,
                IdGenerator.nextLong(),
                sessionId,
                userId,
                StringUtils.hasText(title) ? title.trim() : "新对话",
                Timestamp.from(now),
                Timestamp.from(now));
        return sessionId;
    }

    public Optional<SessionRow> findById(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        List<SessionRow> rows = jdbcTemplate.query("""
                SELECT session_id, user_id, title, message_count, last_active_time
                FROM chat_session
                WHERE session_id = ? AND deleted = FALSE
                """, MAPPER, sessionId.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public SessionRow requireOwned(String sessionId, String userId) {
        SessionRow row = findById(sessionId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "会话不存在"));
        if (!userId.equals(row.userId())) {
            throw new KidoraException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return row;
    }

    public List<SessionRow> listByUser(String userId) {
        return jdbcTemplate.query("""
                SELECT session_id, user_id, title, message_count, last_active_time
                FROM chat_session
                WHERE user_id = ? AND deleted = FALSE
                ORDER BY last_active_time DESC
                LIMIT 50
                """, MAPPER, userId);
    }

    public void touchAndIncrement(String sessionId, int delta) {
        jdbcTemplate.update("""
                UPDATE chat_session
                SET message_count = message_count + ?, last_active_time = ?
                WHERE session_id = ?
                """, delta, Timestamp.from(Instant.now()), sessionId);
    }
}
