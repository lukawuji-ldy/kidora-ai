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
        return insertVersion(lessonSessionId, learnerId, planJson, 1);
    }

    /**
     * 插入指定版本计划。
     *
     * @param lessonSessionId 会话
     * @param learnerId       学习者
     * @param planJson        计划
     * @param version         版本
     * @return planId
     */
    public String insertVersion(String lessonSessionId, String learnerId, String planJson, int version) {
        String planId = IdGenerator.nextBizId("plan_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO cet_training_plan
                (id, plan_id, lesson_session_id, learner_id, version, plan_json, status, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, 'ACTIVE', ?, ?)
                """,
                IdGenerator.nextLong(), planId, lessonSessionId, learnerId, version, planJson, now, now);
        return planId;
    }

    /**
     * 将会话内旧 ACTIVE 计划标记为 SUPERSEDED。
     *
     * @param lessonSessionId 会话
     */
    public void supersedeActiveForSession(String lessonSessionId) {
        jdbcTemplate.update("""
                UPDATE cet_training_plan SET status = 'SUPERSEDED', update_time = ?
                WHERE lesson_session_id = ? AND status = 'ACTIVE'
                """, Timestamp.from(Instant.now()), lessonSessionId);
    }

    /**
     * 当前会话最大版本号。
     *
     * @param lessonSessionId 会话
     * @return 版本，无则 0
     */
    public int findMaxVersion(String lessonSessionId) {
        Integer v = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version), 0) FROM cet_training_plan WHERE lesson_session_id = ?
                """, Integer.class, lessonSessionId);
        return v == null ? 0 : v;
    }

    public Optional<String> findActivePlanJson(String planId) {
        List<String> rows = jdbcTemplate.query("""
                SELECT plan_json::text FROM cet_training_plan WHERE plan_id = ? AND status = 'ACTIVE'
                """, (rs, i) -> rs.getString(1), planId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
