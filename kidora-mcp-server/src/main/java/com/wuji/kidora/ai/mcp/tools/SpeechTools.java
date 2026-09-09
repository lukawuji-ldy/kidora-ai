package com.wuji.kidora.ai.mcp.tools;

import com.wuji.kidora.ai.mcp.speech.SpeechOutcome;
import com.wuji.kidora.ai.mcp.speech.SpeechProvider;
import com.wuji.kidora.ai.mcp.speech.StubSpeechProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 语音类 MCP 工具（委托 {@link SpeechProvider}；JSON 字段名稳定）。
 *
 * @author liudy
 */
@Service
public class SpeechTools {

    /** stub 供应商标识（兼容常量引用） */
    public static final String PROVIDER_STUB = StubSpeechProvider.PROVIDER_ID;

    public static final String STUB_ASR_TEXT = StubSpeechProvider.STUB_ASR_TEXT;

    private final SpeechProvider speechProvider;

    public SpeechTools(SpeechProvider speechProvider) {
        this.speechProvider = speechProvider;
    }

    /**
     * 语音转文本。
     *
     * @param audioBase64 音频 base64（与 audioUrl 二选一）
     * @param audioUrl    音频 URL（与 audioBase64 二选一）
     * @param locale      语言区域，默认 en-US
     * @return JSON：text / confidence / provider 或 error
     */
    @Tool(name = "asr_transcribe",
            description = "Transcribe speech audio to text (stub or Azure Speech)")
    public String asrTranscribe(
            @ToolParam(description = "Audio content as base64", required = false) String audioBase64,
            @ToolParam(description = "Temporary audio URL", required = false) String audioUrl,
            @ToolParam(description = "Locale e.g. en-US", required = false) String locale) {
        if (!StringUtils.hasText(audioBase64) && !StringUtils.hasText(audioUrl)) {
            return SpeechOutcome.error("MISSING_AUDIO", "audioBase64 or audioUrl is required",
                    speechProvider.providerId()).jsonBody();
        }
        return speechProvider.transcribe(audioBase64, audioUrl, locale).jsonBody();
    }

    /**
     * 文本转语音。
     *
     * @param text   待合成文本
     * @param voice  音色（可选）
     * @param locale 语言区域
     * @return JSON：audioBase64 / mimeType / provider 或 error
     */
    @Tool(name = "tts_synthesize",
            description = "Synthesize speech from text (stub or Azure Neural TTS)")
    public String ttsSynthesize(
            @ToolParam(description = "Text to speak") String text,
            @ToolParam(description = "Voice name", required = false) String voice,
            @ToolParam(description = "Locale e.g. en-US", required = false) String locale) {
        if (!StringUtils.hasText(text)) {
            return SpeechOutcome.error("MISSING_TEXT", "text is required",
                    speechProvider.providerId()).jsonBody();
        }
        return speechProvider.synthesize(text, voice, locale).jsonBody();
    }

    /**
     * 发音评测。
     *
     * @param audioBase64   音频 base64
     * @param referenceText 参考文本
     * @param locale        语言区域
     * @return JSON：overall / accuracy / fluency / completeness / provider 或 error
     */
    @Tool(name = "pronunciation_score",
            description = "Score pronunciation against reference text (stub or Azure Pronunciation Assessment)")
    public String pronunciationScore(
            @ToolParam(description = "Audio content as base64") String audioBase64,
            @ToolParam(description = "Expected reference text") String referenceText,
            @ToolParam(description = "Locale e.g. en-US", required = false) String locale) {
        if (!StringUtils.hasText(audioBase64)) {
            return SpeechOutcome.error("MISSING_AUDIO", "audioBase64 is required",
                    speechProvider.providerId()).jsonBody();
        }
        if (!StringUtils.hasText(referenceText)) {
            return SpeechOutcome.error("MISSING_REFERENCE", "referenceText is required",
                    speechProvider.providerId()).jsonBody();
        }
        return speechProvider.scorePronunciation(audioBase64, referenceText, locale).jsonBody();
    }
}
