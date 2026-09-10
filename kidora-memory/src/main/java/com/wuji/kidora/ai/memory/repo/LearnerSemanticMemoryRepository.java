package com.wuji.kidora.ai.memory.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.port.MemoryWritePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * learner_semantic_memory 仓储 + {@link MemoryWritePort}。
 *
 * @author liudy
 */
@Repository
public class LearnerSemanticMemoryRepository implements MemoryWritePort {

    private final JdbcTemplate jdbcTemplate;

    public LearnerSemanticMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String writeSemantic(String learnerId, String content, String sourceSessionId, String extraJson) {
        if (!StringUtils.hasText(learnerId) || !StringUtils.hasText(content)) {
            return null;
        }
        String memoryId = IdGenerator.nextBizId("mem_");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO learner_semantic_memory
                (id, memory_id, learner_id, content, source_session_id, status, extra_json, deleted, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?::jsonb, FALSE, ?, ?)
                """,
                IdGenerator.nextLong(), memoryId, learnerId.trim(), content.trim(), sourceSessionId,
                StringUtils.hasText(extraJson) ? extraJson : "{}", now, now);
        return memoryId;
    }

    @Override
    public List<String> listRecentSemantic(String learnerId, int limit) {
        if (!StringUtils.hasText(learnerId)) {
            return List.of();
        }
        int lim = Math.max(1, Math.min(limit, 20));
        return jdbcTemplate.query("""
                SELECT content FROM learner_semantic_memory
                WHERE learner_id = ? AND deleted = FALSE AND status = 'ACTIVE'
                ORDER BY create_time DESC
                LIMIT ?
                """, (rs, i) -> rs.getString(1), learnerId.trim(), lim);
    }
}
