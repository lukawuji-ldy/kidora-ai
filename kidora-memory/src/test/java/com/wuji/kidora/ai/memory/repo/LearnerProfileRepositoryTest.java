package com.wuji.kidora.ai.memory.repo;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.memory.model.LearnerProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * LearnerProfileRepositoryTest.
 *
 * @author liudy
 */
class LearnerProfileRepositoryTest {

    @Test
    void assertOwnedRejectsOtherParent() {
        LearnerProfile profile = new LearnerProfile(
                "lrn_1", "u_other", "Kid", "6-8", "A1", "emma", "ACTIVE");
        KidoraException ex = assertThrows(KidoraException.class,
                () -> LearnerProfileRepository.assertOwned(profile, "u_demo"));
        assertEquals(ErrorCode.FORBIDDEN_LEARNER, ex.getErrorCode());
    }

    @Test
    void assertOwnedAllowsOwner() {
        LearnerProfile profile = new LearnerProfile(
                "lrn_1", "u_demo", "Kid", "6-8", "A1", "emma", "ACTIVE");
        LearnerProfile loaded = LearnerProfileRepository.assertOwned(profile, "u_demo");
        assertEquals("lrn_1", loaded.learnerId());
    }
}
