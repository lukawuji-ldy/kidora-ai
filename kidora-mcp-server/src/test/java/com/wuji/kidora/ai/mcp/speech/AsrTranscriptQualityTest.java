package com.wuji.kidora.ai.mcp.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ASR 转写质量启发式单测。
 *
 * @author liudy
 */
class AsrTranscriptQualityTest {

    @Test
    void weak_shortEnglishGarbage() {
        assertTrue(AsrTranscriptQuality.isWeak("The."));
        assertTrue(AsrTranscriptQuality.isWeak("a"));
        assertTrue(AsrTranscriptQuality.isWeak("Um"));
        assertTrue(AsrTranscriptQuality.isWeak("..."));
        assertTrue(AsrTranscriptQuality.isWeak(""));
        assertTrue(AsrTranscriptQuality.isWeak(null));
    }

    @Test
    void notWeak_realUtterances() {
        assertFalse(AsrTranscriptQuality.isWeak("My dog is black"));
        assertFalse(AsrTranscriptQuality.isWeak("black"));
        assertFalse(AsrTranscriptQuality.isWeak("我的狗是黑色的，用英文怎么说"));
    }

    @Test
    void pickBetter_prefersChineseHelpOverGarbageEn() {
        assertEquals("我的狗是黑色的，用英文怎么说",
                AsrTranscriptQuality.pickBetter("The.", "我的狗是黑色的，用英文怎么说"));
    }

    @Test
    void pickBetter_keepsStrongEnglish() {
        assertEquals("My dog is black",
                AsrTranscriptQuality.pickBetter("My dog is black", "迈道格伊斯布莱克"));
    }

    @Test
    void tencentEngineType_mapsLocale() {
        assertEquals("16k_en", AsrTranscriptQuality.tencentEngineType("en-US"));
        assertEquals("16k_en", AsrTranscriptQuality.tencentEngineType(null));
        assertEquals("16k_zh", AsrTranscriptQuality.tencentEngineType("zh-CN"));
        assertEquals("16k_zh", AsrTranscriptQuality.tencentEngineType("zh"));
    }

    @Test
    void otherTencentEngine_toggles() {
        assertEquals("16k_zh", AsrTranscriptQuality.otherTencentEngine("16k_en"));
        assertEquals("16k_en", AsrTranscriptQuality.otherTencentEngine("16k_zh"));
    }

    @Test
    void iFlytekLanguage_mapsLocale() {
        assertEquals("en_us", AsrTranscriptQuality.iFlytekLanguage("en-US"));
        assertEquals("en_us", AsrTranscriptQuality.iFlytekLanguage(null));
        assertEquals("zh_cn", AsrTranscriptQuality.iFlytekLanguage("zh-CN"));
    }

    @Test
    void otherIFlytekLanguage_toggles() {
        assertEquals("zh_cn", AsrTranscriptQuality.otherIFlytekLanguage("en_us"));
        assertEquals("en_us", AsrTranscriptQuality.otherIFlytekLanguage("zh_cn"));
    }

    @Test
    void localeForEngine() {
        assertEquals("zh-CN", AsrTranscriptQuality.localeForTencentEngine("16k_zh"));
        assertEquals("en-US", AsrTranscriptQuality.localeForTencentEngine("16k_en"));
        assertEquals("zh-CN", AsrTranscriptQuality.localeForIFlytekLanguage("zh_cn"));
        assertEquals("en-US", AsrTranscriptQuality.localeForIFlytekLanguage("en_us"));
    }
}
