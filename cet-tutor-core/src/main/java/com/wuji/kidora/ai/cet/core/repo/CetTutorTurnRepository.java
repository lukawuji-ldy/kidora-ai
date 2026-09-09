package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * CetTutorTurnRepository.
 *
 * @author liudy
 */
@Repository
public class CetTutorTurnRepository {

    public record TurnRow(String turnId, int turnIndex, String stageId, String tutorText, String childText) {
    }

    private static final RowMapper<TurnRow> MAPPER = (rs, i) -> new TurnRow(
            rs.getString("turn_id"),
            rs.getInt("turn_index"),
            rs.getString("stage_id"),
            rs.getString("tutor_text"),
            rs.getString("child_text")
    );

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

    public List<TurnRow> listRecent(String lessonSessionId, int limit) {
        return jdbcTemplate.query("""
                SELECT turn_id, turn_index, stage_id, tutor_text, child_text
                FROM cet_tutor_turn WHERE lesson_session_id = ?
                ORDER BY turn_index DESC LIMIT ?
                """, MAPPER, lessonSessionId, limit);
    }

    public List<TurnRow> listAll(String lessonSessionId) {
        return jdbcTemplate.query("""
                SELECT turn_id, turn_index, stage_id, tutor_text, child_text
                FROM cet_tutor_turn WHERE lesson_session_id = ?
                ORDER BY turn_index ASC
                """, MAPPER, lessonSessionId);
    }
}
