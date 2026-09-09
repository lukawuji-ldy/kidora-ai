package com.wuji.kidora.ai.mcp.tools;

import com.wuji.kidora.ai.mcp.speech.SpeechProperties;
import com.wuji.kidora.ai.mcp.speech.StubSpeechProvider;
import com.wuji.kidora.ai.mcp.speech.AzureSpeechProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpeechTools 契约单测（默认 stub；azure 未配置错误码）。
 *
 * @author liudy
 */
class SpeechToolsTest {

    private final SpeechTools tools = new SpeechTools(new StubSpeechProvider());

    @Test
    void asr_success_withBase64() {
        String json = tools.asrTranscribe("AAAA", null, "en-US");
        assertTrue(json.contains("\"text\":\"" + SpeechTools.STUB_ASR_TEXT + "\""));
        assertTrue(json.contains("\"provider\":\"stub\""));
        assertTrue(json.contains("\"confidence\":"));
    }

    @Test
    void asr_success_withUrl() {
        String json = tools.asrTranscribe(null, "https://example.com/a.wav", null);
        assertTrue(json.contains("\"text\":\"" + SpeechTools.STUB_ASR_TEXT + "\""));
        assertTrue(json.contains("\"locale\":\"en-US\""));
    }

    @Test
    void asr_missingAudio_returnsError() {
        String json = tools.asrTranscribe(null, null, "en-US");
        assertTrue(json.contains("MISSING_AUDIO"));
        assertTrue(json.contains("\"error\""));
    }

    @Test
    void tts_success() {
        String json = tools.ttsSynthesize("Hello kid", null, "en-US");
        assertTrue(json.contains("\"mimeType\":\"audio/wav\""));
        assertTrue(json.contains("\"provider\":\"stub\""));
        assertTrue(json.contains("\"audioBase64\":\"\""));
    }

    @Test
    void tts_missingText_returnsError() {
        String json = tools.ttsSynthesize("  ", "voice", "en-US");
        assertTrue(json.contains("MISSING_TEXT"));
    }

    @Test
    void pronunciation_success() {
        String json = tools.pronunciationScore("AAAA", "Hello.", "en-US");
        assertTrue(json.contains("\"overall\":85.0"));
        assertTrue(json.contains("\"accuracy\":88.0"));
        assertTrue(json.contains("\"fluency\":82.0"));
        assertTrue(json.contains("\"completeness\":90.0"));
        assertTrue(json.contains("\"provider\":\"stub\""));
    }

    @Test
    void pronunciation_missingReference_returnsError() {
        String json = tools.pronunciationScore("AAAA", "", "en-US");
        assertTrue(json.contains("MISSING_REFERENCE"));
    }

    @Test
    void pronunciation_missingAudio_returnsError() {
        String json = tools.pronunciationScore(null, "Hello.", "en-US");
        assertTrue(json.contains("MISSING_AUDIO"));
    }

    @Test
    void azure_notConfigured_returnsError() {
        SpeechProperties.Azure azure = new SpeechProperties.Azure();
        AzureSpeechProvider provider = new AzureSpeechProvider(azure, WebClient.builder(),
                new ObjectMapper());
        SpeechTools azureTools = new SpeechTools(provider);
        String json = azureTools.asrTranscribe("AAAA", null, "en-US");
        assertTrue(json.contains("AZURE_NOT_CONFIGURED"));
        assertTrue(json.contains("\"provider\":\"azure\""));
    }
}
