package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * CetTrainingPlanRepository.
 *
 * @author liudy
 */
@Repository
public class CetTrainingPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetTrainingPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String insert(String lessonSessionId, String learnerId, String planJson) {
        String planId = IdGenerator.nextBizId("plan_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO cet_training_plan
                (id, plan_id, lesson_session_id, learner_id, version, plan_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, 1, ?::jsonb, 'ACTIVE', ?, ?)
                """,
                IdGenerator.nextLong(), planId, lessonSessionId, learnerId, planJson, now, now);
        return planId;
    }

    public Optional<String> findActivePlanJson(String planId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT plan_json::text FROM cet_training_plan WHERE plan_id = ? AND status = 'ACTIVE'
                """, (rs, i) -> rs.getString(1), planId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
