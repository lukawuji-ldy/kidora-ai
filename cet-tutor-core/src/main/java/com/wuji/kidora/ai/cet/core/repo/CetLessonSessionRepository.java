package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.cet.core.domain.LessonSession;
import com.wuji.kidora.ai.cet.core.domain.LessonStatus;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CetLessonSessionRepository.
 *
 * @author liudy
 */
@Repository
public class CetLessonSessionRepository {

    private static final RowMapper<LessonSession> MAPPER = (rs, rowNum) -> new LessonSession(
            rs.getString("lesson_session_id"),
            rs.getString("user_id"),
            rs.getString("learner_id"),
            rs.getString("topic"),
            rs.getString("persona_id"),
            rs.getString("cefr_level"),
            LessonStatus.valueOf(rs.getString("status")),
            rs.getString("active_plan_id")
    );

    private final JdbcTemplate jdbcTemplate;

    public CetLessonSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LessonSession insert(String userId, String learnerId, String topic, String personaId, String cefr) {
        String sessionId = IdGenerator.nextBizId("cls_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO cet_lesson_session
                (id, lesson_session_id, user_id, learner_id, topic, persona_id, cefr_level, status,
                 active_plan_id, deleted, start_time, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, FALSE, ?, ?, ?)
                """,
                IdGenerator.nextLong(), sessionId, userId, learnerId, topic, personaId, cefr,
                LessonStatus.CREATED.name(), now, now, now);
        return new LessonSession(sessionId, userId, learnerId, topic, personaId, cefr, LessonStatus.CREATED, null);
    }

    public Optional<LessonSession> findById(String lessonSessionId) {
        List<LessonSession> rows = jdbcTemplate.query("""
                SELECT lesson_session_id, user_id, learner_id, topic, persona_id, cefr_level, status, active_plan_id
                FROM cet_lesson_session WHERE lesson_session_id = ? AND deleted = FALSE
                """, MAPPER, lessonSessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void updateStatus(String lessonSessionId, LessonStatus status) {
        jdbcTemplate.update("""
                UPDATE cet_lesson_session SET status = ?, update_time = ?
                WHERE lesson_session_id = ?
                """, status.name(), Timestamp.from(Instant.now()), lessonSessionId);
    }

    public void updateStatusAndPlan(String lessonSessionId, LessonStatus status, String planId) {
        jdbcTemplate.update("""
                UPDATE cet_lesson_session SET status = ?, active_plan_id = ?, update_time = ?
                WHERE lesson_session_id = ?
                """, status.name(), planId, Timestamp.from(Instant.now()), lessonSessionId);
    }

    /**
     * 更新会话扩展 JSON。
     *
     * @param lessonSessionId 会话
     * @param extraJson       JSON 文本
     */
    public void updateExtraJson(String lessonSessionId, String extraJson) {
        jdbcTemplate.update("""
                UPDATE cet_lesson_session SET extra_json = ?::jsonb, update_time = ?
                WHERE lesson_session_id = ?
                """, extraJson, Timestamp.from(Instant.now()), lessonSessionId);
    }

    /**
     * 读取会话扩展 JSON。
     *
     * @param lessonSessionId 会话
     * @return JSON 文本
     */
    public Optional<String> findExtraJson(String lessonSessionId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT extra_json::text FROM cet_lesson_session
                WHERE lesson_session_id = ? AND deleted = FALSE
                """, (rs, i) -> rs.getString(1), lessonSessionId);
        return rows.isEmpty() || rows.get(0) == null ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void markEnded(String lessonSessionId, LessonStatus status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                UPDATE cet_lesson_session SET status = ?, end_time = ?, update_time = ?
                WHERE lesson_session_id = ?
                """, status.name(), now, now, lessonSessionId);
    }
}
