package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CetTutorTurnRepository.
 *
 * @author liudy
 */
@Repository
public class CetTutorTurnRepository {

    /**
     * 轮次行。
     *
     * @param turnId     轮次键
     * @param turnIndex  序号
     * @param stageId    阶段
     * @param tutorText  外教文本
     * @param childText  儿童文本
     * @param createTime 创建时间
     * @author liudy
     */
    public record TurnRow(String turnId, int turnIndex, String stageId, String tutorText, String childText,
                          Instant createTime) {
        public TurnRow(String turnId, int turnIndex, String stageId, String tutorText, String childText) {
            this(turnId, turnIndex, stageId, tutorText, childText, null);
        }
    }

    private static final RowMapper<TurnRow> MAPPER = (rs, i) -> {
        Timestamp ts = rs.getTimestamp("create_time");
        return new TurnRow(
                rs.getString("turn_id"),
                rs.getInt("turn_index"),
                rs.getString("stage_id"),
                rs.getString("tutor_text"),
                rs.getString("child_text"),
                ts == null ? null : ts.toInstant()
        );
    };

    private final JdbcTemplate jdbcTemplate;

    public CetTutorTurnRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int nextTurnIndex(String lessonSessionId) {
        Integer max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(turn_index), 0) FROM cet_tutor_turn WHERE lesson_session_id = ?
                """, Integer.class, lessonSessionId);
        return (max == null ? 0 : max) + 1;
    }

    public String insert(String lessonSessionId, String learnerId, int turnIndex, String stageId,
                         String tutorText, String childText) {
        String turnId = IdGenerator.nextBizId("turn_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO cet_tutor_turn
                (id, turn_id, lesson_session_id, learner_id, turn_index, stage_id, tutor_text, child_text, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                IdGenerator.nextLong(), turnId, lessonSessionId, learnerId, turnIndex, stageId,
                tutorText, childText, now);
        return turnId;
    }

    public void updateTutorText(String turnId, String tutorText) {
        jdbcTemplate.update("UPDATE cet_tutor_turn SET tutor_text = ? WHERE turn_id = ?", tutorText, turnId);
    }

    /**
     * 写入服务端分段耗时 JSON（覆盖整列）。
     *
     * @param turnId     轮次业务键
     * @param timingJson schemaVersion=1 JSON
     */
    public void updateTimingJson(String turnId, String timingJson) {
        jdbcTemplate.update("UPDATE cet_tutor_turn SET timing_json = CAST(? AS jsonb) WHERE turn_id = ?",
                timingJson, turnId);
    }

    /**
     * 按会话+序号读取 timing_json。
     *
     * @param lessonSessionId 会话
     * @param turnIndex       序号
     * @return JSON 文本；无列或无行 empty
     */
    public Optional<String> findTimingJson(String lessonSessionId, int turnIndex) {
        List<String> rows = jdbcTemplate.query("""
                SELECT timing_json::text FROM cet_tutor_turn
                WHERE lesson_session_id = ? AND turn_index = ?
                """, (rs, i) -> rs.getString(1), lessonSessionId, turnIndex);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    /**
     * 合并客户端 e2eHeardMs / reportedAt 到已有 timing_json（幂等覆盖）。
     *
     * @param lessonSessionId 会话
     * @param turnIndex       序号
     * @param e2eHeardMs      停麦到开播毫秒
     * @param reportedAtUtc   ISO-8601 UTC
     * @return 更新行数（0=无行）
     */
    public int mergeClientTiming(String lessonSessionId, int turnIndex, int e2eHeardMs, String reportedAtUtc) {
        return jdbcTemplate.update("""
                UPDATE cet_tutor_turn
                SET timing_json = jsonb_set(
                        jsonb_set(
                                COALESCE(timing_json, '{}'::jsonb),
                                '{client,e2eHeardMs}',
                                to_jsonb(?::int),
                                true),
                        '{client,reportedAt}',
                        to_jsonb(?::text),
                        true)
                WHERE lesson_session_id = ? AND turn_index = ?
                """, e2eHeardMs, reportedAtUtc, lessonSessionId, turnIndex);
    }

    public List<TurnRow> listRecent(String lessonSessionId, int limit) {
        return jdbcTemplate.query("""
                SELECT turn_id, turn_index, stage_id, tutor_text, child_text, create_time
                FROM cet_tutor_turn WHERE lesson_session_id = ?
                ORDER BY turn_index DESC LIMIT ?
                """, MAPPER, lessonSessionId, limit);
    }

    public List<TurnRow> listAll(String lessonSessionId) {
        return jdbcTemplate.query("""
                SELECT turn_id, turn_index, stage_id, tutor_text, child_text, create_time
                FROM cet_tutor_turn WHERE lesson_session_id = ?
                ORDER BY turn_index ASC
                """, MAPPER, lessonSessionId);
    }

    /**
     * 按 turn_index 查单轮。
     *
     * @param lessonSessionId 会话
     * @param turnIndex       序号
     * @return 轮次
     */
    public Optional<TurnRow> findByIndex(String lessonSessionId, int turnIndex) {
        List<TurnRow> rows = jdbcTemplate.query("""
                SELECT turn_id, turn_index, stage_id, tutor_text, child_text, create_time
                FROM cet_tutor_turn WHERE lesson_session_id = ? AND turn_index = ?
                """, MAPPER, lessonSessionId, turnIndex);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
