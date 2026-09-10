package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * cet_training_plan_revision 仓储。
 *
 * @author liudy
 */
@Repository
public class CetTrainingPlanRevisionRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetTrainingPlanRevisionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入修订记录。
     *
     * @param lessonSessionId 会话
     * @param planId          新计划 id
     * @param version         版本
     * @param planJson        计划 JSON
     * @param evalJson        评测 JSON
     * @param reason          原因
     * @return revisionId
     */
    public String insert(String lessonSessionId, String planId, int version,
                         String planJson, String evalJson, String reason) {
        String revisionId = IdGenerator.nextBizId("rev_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO cet_training_plan_revision
                (id, revision_id, lesson_session_id, plan_id, version, plan_json, eval_json, reason, create_time)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)
                """,
                IdGenerator.nextLong(), revisionId, lessonSessionId, planId, version,
                planJson, evalJson, reason, now);
        return revisionId;
    }
}
