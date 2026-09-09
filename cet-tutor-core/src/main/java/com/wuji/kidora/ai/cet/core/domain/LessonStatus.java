package com.wuji.kidora.ai.cet.core.domain;

/**
 * CET 课时会话状态。
 *
 * @author liudy
 */
public enum LessonStatus {
    CREATED,
    PLANNING,
    PRACTICING,
    EVALUATING,
    REPLANNING,
    COMPLETED,
    ABORTED,
    SAFETY_BLOCKED
}
