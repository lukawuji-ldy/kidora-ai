package com.wuji.kidora.ai.cet.core.domain;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LessonStateMachineTest.
 *
 * @author liudy
 */
class LessonStateMachineTest {

    @Test
    void allowsOpenPath() {
        assertDoesNotThrow(() -> LessonStateMachine.assertTransition(LessonStatus.CREATED, LessonStatus.PLANNING));
        assertDoesNotThrow(() -> LessonStateMachine.assertTransition(LessonStatus.PLANNING, LessonStatus.PRACTICING));
        assertDoesNotThrow(() -> LessonStateMachine.assertTransition(LessonStatus.PRACTICING, LessonStatus.EVALUATING));
        assertDoesNotThrow(() -> LessonStateMachine.assertTransition(LessonStatus.EVALUATING, LessonStatus.COMPLETED));
    }

    @Test
    void rejectsInvalidJump() {
        KidoraException ex = assertThrows(KidoraException.class,
                () -> LessonStateMachine.assertTransition(LessonStatus.CREATED, LessonStatus.COMPLETED));
        assertEquals(ErrorCode.CET_INVALID_STATE, ex.getErrorCode());
    }
}
