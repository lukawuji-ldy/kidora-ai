package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EnglishLevelMapperTest.
 *
 * @author liudy
 */
class EnglishLevelMapperTest {

    @Test
    void mapsAllFourLevels() {
        assertEquals("A0", EnglishLevelMapper.toCefr("BEGINNER"));
        assertEquals("A1", EnglishLevelMapper.toCefr("ELEMENTARY"));
        assertEquals("A2", EnglishLevelMapper.toCefr("INTERMEDIATE"));
        assertEquals("B1", EnglishLevelMapper.toCefr("ADVANCED"));
    }

    @Test
    void acceptsLowerCase() {
        assertEquals("A1", EnglishLevelMapper.toCefr("elementary"));
    }

    @Test
    void rejectsBlankAndUnknown() {
        KidoraException blank = assertThrows(KidoraException.class, () -> EnglishLevelMapper.toCefr(" "));
        assertEquals(ErrorCode.BAD_REQUEST, blank.getErrorCode());
        KidoraException unknown = assertThrows(KidoraException.class, () -> EnglishLevelMapper.toCefr("C2"));
        assertEquals(ErrorCode.BAD_REQUEST, unknown.getErrorCode());
    }

    @Test
    void fromCefrRoundTrips() {
        assertEquals("BEGINNER", EnglishLevelMapper.fromCefr("A0"));
        assertEquals("ELEMENTARY", EnglishLevelMapper.fromCefr("a1"));
        assertEquals("INTERMEDIATE", EnglishLevelMapper.fromCefr("A2"));
        assertEquals("ADVANCED", EnglishLevelMapper.fromCefr("B1"));
    }

    @Test
    void fromCefrRejectsUnknown() {
        KidoraException ex = assertThrows(KidoraException.class, () -> EnglishLevelMapper.fromCefr("C1"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }
}
