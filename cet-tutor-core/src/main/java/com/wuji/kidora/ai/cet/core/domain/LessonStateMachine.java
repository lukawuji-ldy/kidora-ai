package com.wuji.kidora.ai.cet.core.domain;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 课时状态机：校验合法迁移。
 *
 * @author liudy
 */
public final class LessonStateMachine {

    private static final Map<LessonStatus, Set<LessonStatus>> EDGES = new EnumMap<>(LessonStatus.class);

    static {
        EDGES.put(LessonStatus.CREATED, EnumSet.of(LessonStatus.PLANNING, LessonStatus.ABORTED, LessonStatus.SAFETY_BLOCKED));
        EDGES.put(LessonStatus.PLANNING, EnumSet.of(LessonStatus.PRACTICING, LessonStatus.ABORTED, LessonStatus.SAFETY_BLOCKED));
        EDGES.put(LessonStatus.PRACTICING, EnumSet.of(
                LessonStatus.EVALUATING, LessonStatus.ABORTED, LessonStatus.SAFETY_BLOCKED, LessonStatus.PRACTICING));
        EDGES.put(LessonStatus.EVALUATING, EnumSet.of(
                LessonStatus.COMPLETED, LessonStatus.REPLANNING, LessonStatus.PRACTICING,
                LessonStatus.ABORTED, LessonStatus.SAFETY_BLOCKED));
        EDGES.put(LessonStatus.REPLANNING, EnumSet.of(LessonStatus.PRACTICING, LessonStatus.ABORTED, LessonStatus.SAFETY_BLOCKED));
        EDGES.put(LessonStatus.COMPLETED, EnumSet.noneOf(LessonStatus.class));
        EDGES.put(LessonStatus.ABORTED, EnumSet.noneOf(LessonStatus.class));
        EDGES.put(LessonStatus.SAFETY_BLOCKED, EnumSet.noneOf(LessonStatus.class));
    }

    private LessonStateMachine() {
    }

    public static void assertTransition(LessonStatus from, LessonStatus to) {
        Set<LessonStatus> allowed = EDGES.getOrDefault(from, EnumSet.noneOf(LessonStatus.class));
        if (!allowed.contains(to)) {
            throw new KidoraException(ErrorCode.CET_INVALID_STATE,
                    "非法状态迁移: " + from + " -> " + to);
        }
    }

    public static boolean canTransition(LessonStatus from, LessonStatus to) {
        return EDGES.getOrDefault(from, EnumSet.noneOf(LessonStatus.class)).contains(to);
    }
}
