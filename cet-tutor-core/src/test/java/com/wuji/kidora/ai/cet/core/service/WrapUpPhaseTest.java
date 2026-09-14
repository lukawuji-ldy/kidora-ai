package com.wuji.kidora.ai.cet.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WrapUpPhase} 单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class WrapUpPhaseTest {

    @Mock
    CetLessonSessionRepository repository;

    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void seedLastEval() {
        when(repository.findExtraJson("cls_1"))
                .thenReturn(Optional.of("{\"lastStageEvalTurn\":5}"));
    }

    @Test
    void setPendingTutorFarewell_mergesLastStageEvalTurn() {
        WrapUpPhase.setPendingTutorFarewell(repository, "cls_1", "今天很棒！", mapper);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(repository).updateExtraJson(eq("cls_1"), cap.capture());
        assertTrue(cap.getValue().contains("\"lastStageEvalTurn\":5"));
        assertTrue(cap.getValue().contains("\"wrapUpPhase\":\"pending_tutor_farewell\""));
        assertTrue(cap.getValue().contains("\"pendingChildSummary\":\"今天很棒！\""));
    }

    @Test
    void setAwaitChildFarewell_keepsPendingSummary() {
        when(repository.findExtraJson("cls_1")).thenReturn(Optional.of(
                "{\"lastStageEvalTurn\":5,\"wrapUpPhase\":\"pending_tutor_farewell\","
                        + "\"pendingChildSummary\":\"摘要\"}"));
        WrapUpPhase.setAwaitChildFarewell(repository, "cls_1", mapper);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(repository).updateExtraJson(eq("cls_1"), cap.capture());
        assertTrue(cap.getValue().contains("\"wrapUpPhase\":\"await_child_farewell\""));
        assertTrue(cap.getValue().contains("\"pendingChildSummary\":\"摘要\""));
    }

    @Test
    void clear_removesWrapUpKeysOnly() {
        when(repository.findExtraJson("cls_1")).thenReturn(Optional.of(
                "{\"lastStageEvalTurn\":3,\"wrapUpPhase\":\"await_child_farewell\","
                        + "\"pendingChildSummary\":\"x\"}"));
        WrapUpPhase.clear(repository, "cls_1", mapper);
        ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
        verify(repository).updateExtraJson(eq("cls_1"), cap.capture());
        assertTrue(cap.getValue().contains("\"lastStageEvalTurn\":3"));
        assertTrue(!cap.getValue().contains("wrapUpPhase"));
        assertTrue(!cap.getValue().contains("pendingChildSummary"));
    }

    @Test
    void readPhase_returnsValue() {
        when(repository.findExtraJson("cls_1")).thenReturn(Optional.of(
                "{\"wrapUpPhase\":\"await_child_farewell\"}"));
        assertEquals(WrapUpPhase.AWAIT_CHILD_FAREWELL,
                WrapUpPhase.readPhase(repository, "cls_1", mapper).orElseThrow());
    }
}
