package com.wuji.kidora.ai.memory.repo;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.common.util.IdGenerator;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.port.LearnerProfilePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * learner_profile 读仓储 + 归属校验 + extra_json 写入。
 *
 * @author liudy
 */
@Repository
public class LearnerProfileRepository implements LearnerProfilePort {

    private static final RowMapper<LearnerProfile> MAPPER = (rs, rowNum) -> new LearnerProfile(
            rs.getString("learner_id"),
            rs.getString("user_id"),
            rs.getString("display_name"),
            rs.getString("age_band"),
            rs.getString("cefr_level"),
            rs.getString("preferred_persona"),
            rs.getString("status"),
            rs.getString("extra_json")
    );

    private final JdbcTemplate jdbcTemplate;

    public LearnerProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LearnerProfile> findByLearnerId(String learnerId) {
        if (!StringUtils.hasText(learnerId)) {
            return Optional.empty();
        }
        List<LearnerProfile> rows = jdbcTemplate.query("""
                SELECT learner_id, user_id, display_name, age_band, cefr_level, preferred_persona, status,
                       extra_json::text AS extra_json
                FROM learner_profile
                WHERE learner_id = ? AND deleted = FALSE
                """, MAPPER, learnerId.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * 新建学习者档案（注册建档）。
     *
     * @param learnerId        业务键
     * @param userId           归属家长
     * @param displayName      儿童昵称
     * @param ageBand          年龄段，可为 null
     * @param cefrLevel        CEFR
     * @param preferredPersona 默认人设
     * @author liudy
     */
    public void insert(String learnerId,
                       String userId,
                       String displayName,
                       String ageBand,
                       String cefrLevel,
                       String preferredPersona) {
        if (!StringUtils.hasText(learnerId) || !StringUtils.hasText(userId) || !StringUtils.hasText(displayName)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "learnerId / userId / displayName 不能为空");
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO learner_profile (id, learner_id, user_id, display_name, age_band, cefr_level,
                                             preferred_persona, extra_json, status, deleted, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, 'ACTIVE', FALSE, ?, ?)
                """,
                IdGenerator.nextLong(),
                learnerId.trim(),
                userId.trim(),
                displayName.trim(),
                ageBand,
                cefrLevel,
                preferredPersona,
                now,
                now);
    }

    @Override
    public void updateExtraJson(String learnerId, String extraJson) {
        if (!StringUtils.hasText(learnerId)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "learnerId 不能为空");
        }
        int n = jdbcTemplate.update("""
                UPDATE learner_profile SET extra_json = ?::jsonb, update_time = ?
                WHERE learner_id = ? AND deleted = FALSE
                """, extraJson, Timestamp.from(Instant.now()), learnerId.trim());
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "学习者不存在");
        }
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
                SELECT learner_id, user_id, display_name, age_band, cefr_level, preferred_persona, status,
                       extra_json::text AS extra_json
                FROM learner_profile
                WHERE user_id = ? AND deleted = FALSE AND status = 'ACTIVE'
                ORDER BY create_time ASC
                """, MAPPER, userId.trim());
    }

    /**
     * 统计家长名下 ACTIVE 且未删儿童数。
     *
     * @author liudy
     */
    public int countActiveByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM learner_profile
                WHERE user_id = ? AND deleted = FALSE AND status = 'ACTIVE'
                """, Integer.class, userId.trim());
        return n == null ? 0 : n;
    }

    /**
     * 更新昵称与 CEFR（个人中心编辑）。
     *
     * @author liudy
     */
    public void updateProfile(String learnerId, String displayName, String cefrLevel) {
        if (!StringUtils.hasText(learnerId) || !StringUtils.hasText(displayName) || !StringUtils.hasText(cefrLevel)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "learnerId / displayName / cefrLevel 不能为空");
        }
        int n = jdbcTemplate.update("""
                UPDATE learner_profile SET display_name = ?, cefr_level = ?, update_time = ?
                WHERE learner_id = ? AND deleted = FALSE
                """, displayName.trim(), cefrLevel.trim(), Timestamp.from(Instant.now()), learnerId.trim());
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "学习者不存在");
        }
    }

    /**
     * 软删除学习者（deleted = TRUE）。
     *
     * @author liudy
     */
    public void softDelete(String learnerId) {
        if (!StringUtils.hasText(learnerId)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "learnerId 不能为空");
        }
        int n = jdbcTemplate.update("""
                UPDATE learner_profile SET deleted = TRUE, status = 'DISABLED', update_time = ?
                WHERE learner_id = ? AND deleted = FALSE
                """, Timestamp.from(Instant.now()), learnerId.trim());
        if (n == 0) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "学习者不存在");
        }
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
