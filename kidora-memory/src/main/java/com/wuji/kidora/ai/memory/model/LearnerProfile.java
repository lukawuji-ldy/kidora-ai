package com.wuji.kidora.ai.memory.model;

/**
 * 儿童学习者档案视图。
 *
 * @author liudy
 */
public record LearnerProfile(
        String learnerId,
        String userId,
        String displayName,
        String ageBand,
        String cefrLevel,
        String preferredPersona,
        String status
) {
}
