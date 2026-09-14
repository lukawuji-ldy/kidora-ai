package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.cet.core.domain.LessonSession;
import com.wuji.kidora.ai.cet.core.domain.LessonStatus;
import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * CetLessonSessionRepository.
 *
 * @author liudy
 */
@Repository
public class CetLessonSessionRepository {

    /**
     * 课时列表行（含报告是否存在）。
     *
     * @param sessionId   会话键
     * @param learnerId   学习者
     * @param topic       主题
     * @param personaId   人设
     * @param cefrLevel   CEFR
     * @param status      状态
     * @param createTime  创建时间
     * @param startTime   开课时间
     * @param endTime     结课时间
     * @param hasReport   是否已有报告
     * @author liudy
     */
    public record SessionListItem(String sessionId, String learnerId, String topic, String personaId,
                                  String cefrLevel, String status, Instant createTime, Instant startTime,
                                  Instant endTime, boolean hasReport) {
    }

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

    private static final RowMapper<SessionListItem> LIST_MAPPER = (rs, rowNum) -> new SessionListItem(
            rs.getString("lesson_session_id"),
            rs.getString("learner_id"),
            rs.getString("topic"),
            rs.getString("persona_id"),
            rs.getString("cefr_level"),
            rs.getString("status"),
            toInstant(rs.getTimestamp("create_time")),
            toInstant(rs.getTimestamp("start_time")),
            toInstant(rs.getTimestamp("end_time")),
            rs.getBoolean("has_report")
    );

    private final JdbcTemplate jdbcTemplate;

    public CetLessonSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
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

    public Optional<Instant> findEndTime(String lessonSessionId) {
        List<Instant> rows = jdbcTemplate.query("""
                SELECT end_time FROM cet_lesson_session
                WHERE lesson_session_id = ? AND deleted = FALSE
                """, (rs, i) -> toInstant(rs.getTimestamp("end_time")), lessonSessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
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

    /**
     * 按学习者列出课时（时间倒序）。
     *
     * @param userId    归属用户
     * @param learnerId 学习者
     * @return 列表
     */
    public List<SessionListItem> listByLearner(String userId, String learnerId) {
        return jdbcTemplate.query("""
                SELECT s.lesson_session_id, s.learner_id, s.topic, s.persona_id, s.cefr_level, s.status,
                       s.create_time, s.start_time, s.end_time,
                       EXISTS (
                           SELECT 1 FROM cet_session_report r
                           WHERE r.lesson_session_id = s.lesson_session_id
                       ) AS has_report
                FROM cet_lesson_session s
                WHERE s.user_id = ? AND s.learner_id = ? AND s.deleted = FALSE
                ORDER BY s.create_time DESC
                """, LIST_MAPPER, userId, learnerId);
    }

    /**
     * 按业务键硬删除会话及其子表行（不含 llm_call_log）。调用方须在事务中执行。
     *
     * @param lessonSessionIds 去重后的会话键
     * @return 主表删除行数
     */
    public int hardDeleteCascade(Collection<String> lessonSessionIds) {
        if (lessonSessionIds == null || lessonSessionIds.isEmpty()) {
            return 0;
        }
        String placeholders = String.join(",", lessonSessionIds.stream().map(id -> "?").toList());
        Object[] args = lessonSessionIds.toArray();
        jdbcTemplate.update(
                "DELETE FROM cet_tutor_turn WHERE lesson_session_id IN (" + placeholders + ")", args);
        jdbcTemplate.update(
                "DELETE FROM cet_turn_assessment WHERE lesson_session_id IN (" + placeholders + ")", args);
        jdbcTemplate.update(
                "DELETE FROM cet_session_report WHERE lesson_session_id IN (" + placeholders + ")", args);
        jdbcTemplate.update(
                "DELETE FROM cet_training_plan_revision WHERE lesson_session_id IN (" + placeholders + ")", args);
        jdbcTemplate.update(
                "DELETE FROM cet_training_plan WHERE lesson_session_id IN (" + placeholders + ")", args);
        jdbcTemplate.update(
                "DELETE FROM cet_safety_event WHERE lesson_session_id IN (" + placeholders + ")", args);
        return jdbcTemplate.update(
                "DELETE FROM cet_lesson_session WHERE lesson_session_id IN (" + placeholders + ")", args);
    }
}
