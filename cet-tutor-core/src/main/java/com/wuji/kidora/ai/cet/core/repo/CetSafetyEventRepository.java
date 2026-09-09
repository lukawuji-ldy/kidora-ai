package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * CetSafetyEventRepository.
 *
 * @author liudy
 */
@Repository
public class CetSafetyEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetSafetyEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String lessonSessionId, String turnId, String learnerId, String userId,
                       String direction, String eventType, String action, String detailJson) {
        jdbcTemplate.update("""
                INSERT INTO cet_safety_event
                (id, event_id, lesson_session_id, turn_id, learner_id, user_id, direction, event_type, action, detail_json, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                IdGenerator.nextLong(),
                IdGenerator.nextBizId("safe_"),
                lessonSessionId,
                turnId,
                learnerId,
                userId,
                direction,
                eventType,
                action,
                detailJson == null ? "{}" : detailJson,
                Timestamp.from(Instant.now()));
    }
}
