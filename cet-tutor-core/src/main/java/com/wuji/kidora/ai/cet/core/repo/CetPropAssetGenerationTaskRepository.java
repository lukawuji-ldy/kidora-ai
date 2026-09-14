package com.wuji.kidora.ai.cet.core.repo;

import com.wuji.kidora.ai.common.util.IdGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * CET 道具缺失生成任务写入仓储。
 *
 * <p>运行时只创建 QUEUED 任务，不调用图片生成服务；生成、审核和发布由管理仓处理。</p>
 *
 * @author liudy
 */
@Repository
public class CetPropAssetGenerationTaskRepository {

    private final JdbcTemplate jdbcTemplate;

    public CetPropAssetGenerationTaskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 幂等写入缺失 lemma 的生成任务。
     *
     * @param lemmas         缺失 lemma
     * @param theme          主题目录
     * @param sourcePlanId   来源计划
     * @param sourceSessionId 来源课时
     */
    public void enqueueMissing(Collection<String> lemmas,
                               String theme,
                               String sourcePlanId,
                               String sourceSessionId) {
        if (lemmas == null || lemmas.isEmpty()) {
            return;
        }
        String safeTheme = StringUtils.hasText(theme) ? theme.trim() : "default";
        Timestamp now = Timestamp.from(Instant.now());
        Set<String> normalizedLemmas = new LinkedHashSet<>();
        for (String rawLemma : lemmas) {
            if (StringUtils.hasText(rawLemma)) {
                normalizedLemmas.add(rawLemma.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String lemma : normalizedLemmas) {
            try {
                jdbcTemplate.update("""
                        INSERT INTO cet_prop_asset_generation_task
                        (id, task_id, lemma, aliases_json, theme, source_plan_id,
                         source_session_id, version, status, create_time, update_time)
                        SELECT ?, ?, ?, '[]'::jsonb, ?, ?, ?, 
                               COALESCE(MAX(version), 0) + 1, 'QUEUED', ?, ?
                        FROM cet_prop_asset_generation_task
                        WHERE lemma = ? AND theme = ?
                          AND NOT EXISTS (
                              SELECT 1 FROM cet_prop_asset
                              WHERE lemma = ? AND status = 'ACTIVE'
                          )
                          AND NOT EXISTS (
                              SELECT 1
                              FROM cet_prop_asset_generation_task
                              WHERE lemma = ? AND theme = ?
                                AND status IN ('QUEUED', 'GENERATING', 'PENDING_REVIEW')
                          )
                        """,
                        IdGenerator.nextLong(),
                        IdGenerator.nextBizId("prop_task_"),
                        lemma,
                        safeTheme,
                        sourcePlanId,
                        sourceSessionId,
                        now,
                        now,
                        lemma,
                        safeTheme,
                        lemma,
                        lemma,
                        safeTheme);
            } catch (DataIntegrityViolationException ignored) {
                // 并发计划已创建相同 lemma 的任务时，唯一索引保证幂等。
            }
        }
    }
}
