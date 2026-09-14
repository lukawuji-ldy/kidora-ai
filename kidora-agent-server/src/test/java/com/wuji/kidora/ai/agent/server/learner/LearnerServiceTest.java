package com.wuji.kidora.ai.agent.server.learner;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LearnerService 个人中心儿童档案单测。
 *
 * @author liudy
 */
@ExtendWith(MockitoExtension.class)
class LearnerServiceTest {

    @Mock
    private LearnerProfileRepository learnerProfileRepository;

    private LearnerService learnerService;

    @BeforeEach
    void setUp() {
        learnerService = new LearnerService(learnerProfileRepository);
    }

    @Test
    void listIncludesEnglishLevel() {
        when(learnerProfileRepository.listActiveByUserId("u_1")).thenReturn(List.of(
                new LearnerProfile("lrn_1", "u_1", "小明", null, "A1", "emma", "ACTIVE")));

        List<Map<String, Object>> items = learnerService.listActive("u_1");

        assertEquals(1, items.size());
        assertEquals("ELEMENTARY", items.get(0).get("englishLevel"));
        assertEquals("A1", items.get(0).get("cefrLevel"));
        assertEquals("小明", items.get(0).get("displayName"));
    }

    @Test
    void createInsertsWithMappedCefr() {
        Map<String, Object> created = learnerService.create("u_1", "Amy", "BEGINNER");

        assertTrue(String.valueOf(created.get("learnerId")).startsWith("lrn_"));
        assertEquals("BEGINNER", created.get("englishLevel"));
        assertEquals("A0", created.get("cefrLevel"));
        ArgumentCaptor<String> idCap = ArgumentCaptor.forClass(String.class);
        verify(learnerProfileRepository).insert(
                idCap.capture(), eq("u_1"), eq("Amy"), isNull(), eq("A0"), eq("emma"));
        assertEquals(created.get("learnerId"), idCap.getValue());
    }

    @Test
    void updateChangesNicknameAndLevel() {
        when(learnerProfileRepository.requireOwned("lrn_1", "u_1")).thenReturn(
                new LearnerProfile("lrn_1", "u_1", "旧名", null, "A1", "emma", "ACTIVE"));

        Map<String, Object> updated = learnerService.update(
                "u_1", "lrn_1", "新名", "ADVANCED");

        assertEquals("新名", updated.get("displayName"));
        assertEquals("ADVANCED", updated.get("englishLevel"));
        assertEquals("B1", updated.get("cefrLevel"));
        verify(learnerProfileRepository).updateProfile("lrn_1", "新名", "B1");
    }

    @Test
    void softDeleteRejectsLastActiveChild() {
        when(learnerProfileRepository.requireOwned("lrn_1", "u_1")).thenReturn(
                new LearnerProfile("lrn_1", "u_1", "唯一", null, "A1", "emma", "ACTIVE"));
        when(learnerProfileRepository.countActiveByUserId("u_1")).thenReturn(1);

        KidoraException ex = assertThrows(KidoraException.class,
                () -> learnerService.softDelete("u_1", "lrn_1"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        verify(learnerProfileRepository, never()).softDelete(any());
    }

    @Test
    void softDeleteWhenMultipleActive() {
        when(learnerProfileRepository.requireOwned("lrn_1", "u_1")).thenReturn(
                new LearnerProfile("lrn_1", "u_1", "A", null, "A1", "emma", "ACTIVE"));
        when(learnerProfileRepository.countActiveByUserId("u_1")).thenReturn(2);

        learnerService.softDelete("u_1", "lrn_1");

        verify(learnerProfileRepository).softDelete("lrn_1");
    }
}
