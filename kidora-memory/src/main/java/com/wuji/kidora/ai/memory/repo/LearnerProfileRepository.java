package com.wuji.kidora.ai.memory.repo;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * learner_profile 读仓储 + 归属校验。
 *
 * @author liudy
 */
@Repository
public class LearnerProfileRepository {

    private static final RowMapper<LearnerProfile> MAPPER = (rs, rowNum) -> new LearnerProfile(
            rs.getString("learner_id"),
            rs.getString("user_id"),
            rs.getString("display_name"),
            rs.getString("age_band"),
            rs.getString("cefr_level"),
            rs.getString("preferred_persona"),
            rs.getString("status")
    );

    private final JdbcTemplate jdbcTemplate;

    public LearnerProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LearnerProfile> findByLearnerId(String learnerId) {
        if (!StringUtils.hasText(learnerId)) {
            return Optional.empty();
        }
        List<LearnerProfile> rows = jdbcTemplate.query("""
                SELECT learner_id, user_id, display_name, age_band, cefr_level, preferred_persona, status
                FROM learner_profile
                WHERE learner_id = ? AND deleted = FALSE
                """, MAPPER, learnerId.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 加载档案并断言归属当前家长用户。
     *
     * @author liudy
     */
    public LearnerProfile requireOwned(String learnerId, String userId) {
        LearnerProfile profile = findByLearnerId(learnerId)
                .orElseThrow(() -> new KidoraException(ErrorCode.NOT_FOUND, "学习者不存在"));
        return assertOwned(profile, userId);
    }

    /**
     * 列出家长名下 ACTIVE 学习者。
     *
     * @author liudy
     */
    public List<LearnerProfile> listActiveByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT learner_id, user_id, display_name, age_band, cefr_level, preferred_persona, status
                FROM learner_profile
                WHERE user_id = ? AND deleted = FALSE AND status = 'ACTIVE'
                ORDER BY create_time ASC
                """, MAPPER, userId.trim());
    }

    /**
     * 断言档案归属（供单测直接覆盖）。
     *
     * @author liudy
     */
    public static LearnerProfile assertOwned(LearnerProfile profile, String userId) {
        if (profile == null) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "学习者不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(profile.status())) {
            throw new KidoraException(ErrorCode.FORBIDDEN, "学习者已禁用");
        }
        if (!StringUtils.hasText(userId) || !userId.equals(profile.userId())) {
            throw new KidoraException(ErrorCode.FORBIDDEN_LEARNER);
        }
        return profile;
    }
}
