package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CetAssessmentRepository.
 *
 * @author liudy
 */
@Repository
public class CetAssessmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetAssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String insertSession(String lessonSessionId, String assessmentJson) {
        String id = IdGenerator.nextBizId("asm_");
        jdbcTemplate.update("""
                INSERT INTO cet_turn_assessment
                (id, assessment_id, lesson_session_id, turn_id, scope, assessment_json, create_time)
                VALUES (?, ?, ?, NULL, 'SESSION', ?::jsonb, ?)
                """,
                IdGenerator.nextLong(), id, lessonSessionId, assessmentJson, Timestamp.from(Instant.now()));
        return id;
    }

    public Optional<String> findLatestSessionJson(String lessonSessionId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT assessment_json::text FROM cet_turn_assessment
                WHERE lesson_session_id = ? AND scope = 'SESSION'
                ORDER BY create_time DESC LIMIT 1
                """, (rs, i) -> rs.getString(1), lessonSessionId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
