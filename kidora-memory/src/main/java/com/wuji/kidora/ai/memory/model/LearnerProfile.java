package com.wuji.kidora.ai.memory.model;

/**
 * 儿童学习者档案视图。
 *
 * @param learnerId         学习者键
 * @param userId            归属家长
 * @param displayName       展示名
 * @param ageBand           年龄段
 * @param cefrLevel         CEFR
 * @param preferredPersona  人设
 * @param status            状态
 * @param extraJson         扩展画像 JSON（MVP-3）
 * @author liudy
 */
public record LearnerProfile(
        String learnerId,
        String userId,
        String displayName,
        String ageBand,
        String cefrLevel,
        String preferredPersona,
        String status,
        String extraJson
) {
    /**
     * 兼容旧构造（无 extraJson）。
     */
    public LearnerProfile(String learnerId, String userId, String displayName, String ageBand,
                          String cefrLevel, String preferredPersona, String status) {
        this(learnerId, userId, displayName, ageBand, cefrLevel, preferredPersona, status, null);
    }
}
