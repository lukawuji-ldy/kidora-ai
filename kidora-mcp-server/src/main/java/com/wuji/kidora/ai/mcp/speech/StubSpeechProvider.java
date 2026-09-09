package com.wuji.kidora.ai.mcp.speech;

import org.springframework.util.StringUtils;

/**
 * Stub 语音供应商（固定假数据；字段与 MVP-2A 契约一致）。
 *
 * @author liudy
 */
public class StubSpeechProvider implements SpeechProvider {

    public static final String PROVIDER_ID = "stub";

    public static final String STUB_ASR_TEXT = "Hello.";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"text\":\"" + STUB_ASR_TEXT + "\",\"confidence\":0.99,\"locale\":\""
                + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    @Override
    public SpeechOutcome synthesize(String text, String voice, String locale) {
        String loc = defaultLocale(locale);
        String voiceName = StringUtils.hasText(voice) ? voice : "en-US-AvaNeural";
        return SpeechOutcome.ok("{\"audioBase64\":\"\",\"mimeType\":\"audio/wav\",\"voice\":\""
                + escape(voiceName) + "\",\"locale\":\"" + escape(loc)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    @Override
    public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"overall\":85.0,\"accuracy\":88.0,\"fluency\":82.0,\"completeness\":90.0,\"locale\":\""
                + escape(loc) + "\",\"referenceText\":\"" + escape(referenceText)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    private static String defaultLocale(String locale) {
        return StringUtils.hasText(locale) ? locale.trim() : "en-US";
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
