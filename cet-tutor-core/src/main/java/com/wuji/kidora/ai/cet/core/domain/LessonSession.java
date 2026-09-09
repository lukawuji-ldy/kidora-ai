package com.wuji.kidora.ai.cet.core.domain;

/**
 * LessonSession.
 *
 * @author liudy
 */
public record LessonSession(
        String lessonSessionId,
        String userId,
        String learnerId,
        String topic,
        String personaId,
        String cefrLevel,
        LessonStatus status,
        String activePlanId
) {
}
