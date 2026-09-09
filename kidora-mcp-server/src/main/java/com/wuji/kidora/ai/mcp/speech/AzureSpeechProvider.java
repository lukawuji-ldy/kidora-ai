package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Azure Speech REST 供应商（短音频 STT / Neural TTS / Pronunciation-Assessment）。
 *
 * @author liudy
 */
public class AzureSpeechProvider implements SpeechProvider {

    public static final String PROVIDER_ID = "azure";

    private static final Logger log = LoggerFactory.getLogger(AzureSpeechProvider.class);

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private static final Duration URL_FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final SpeechProperties.Azure azure;

    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    public AzureSpeechProvider(SpeechProperties.Azure azure, WebClient.Builder webClientBuilder,
                               ObjectMapper objectMapper) {
        this.azure = azure;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 测试或自定义 WebClient 注入。
     *
     * @param azure            Azure 配置
     * @param webClient        WebClient
     * @param objectMapper     JSON
     */
    public AzureSpeechProvider(SpeechProperties.Azure azure, WebClient webClient, ObjectMapper objectMapper) {
        this.azure = azure;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
        SpeechOutcome cfg = requireConfigured();
        if (cfg != null) {
            return cfg;
        }
        try {
            byte[] audio = resolveAudioBytes(audioBase64, audioUrl);
            String loc = defaultLocale(locale);
            String url = sttUrl(loc);
            String body = webClient.post()
                    .uri(url)
                    .header("Ocp-Apim-Subscription-Key", azure.getKey().trim())
                    .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(audio)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(HTTP_TIMEOUT);
            return mapAsrResponse(body, loc);
        } catch (WebClientResponseException e) {
            log.warn("Azure ASR HTTP {} {}", e.getStatusCode().value(), safeSnippet(e.getResponseBodyAsString()));
            return SpeechOutcome.error("AZURE_ASR_FAILED", "Azure speech-to-text failed: HTTP "
                    + e.getStatusCode().value(), PROVIDER_ID);
        } catch (Exception e) {
            log.warn("Azure ASR failed: {}", e.getMessage());
            return SpeechOutcome.error("AZURE_ASR_FAILED", "Azure speech-to-text failed", PROVIDER_ID);
        }
    }

    @Override
    public SpeechOutcome synthesize(String text, String voice, String locale) {
        SpeechOutcome cfg = requireConfigured();
        if (cfg != null) {
            return cfg;
        }
        try {
            String loc = defaultLocale(locale);
            String voiceName = StringUtils.hasText(voice) ? voice.trim() : azure.getDefaultVoice();
            String ssml = "<speak version='1.0' xml:lang='" + xmlEscape(loc) + "'>"
                    + "<voice name='" + xmlEscape(voiceName) + "'>" + xmlEscape(text) + "</voice></speak>";
            String url = ttsUrl();
            byte[] audio = webClient.post()
                    .uri(url)
                    .header("Ocp-Apim-Subscription-Key", azure.getKey().trim())
                    .header(HttpHeaders.CONTENT_TYPE, "application/ssml+xml")
                    .header("X-Microsoft-OutputFormat", "riff-16khz-16bit-mono-pcm")
                    .header(HttpHeaders.USER_AGENT, "kidora-mcp-server")
                    .bodyValue(ssml.getBytes(StandardCharsets.UTF_8))
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(HTTP_TIMEOUT);
            if (audio == null || audio.length == 0) {
                return SpeechOutcome.error("AZURE_TTS_FAILED", "Empty TTS response", PROVIDER_ID);
            }
            String b64 = Base64.getEncoder().encodeToString(audio);
            return SpeechOutcome.ok("{\"audioBase64\":\"" + b64 + "\",\"mimeType\":\"audio/wav\",\"voice\":\""
                    + escape(voiceName) + "\",\"locale\":\"" + escape(loc)
                    + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
        } catch (WebClientResponseException e) {
            log.warn("Azure TTS HTTP {} {}", e.getStatusCode().value(), safeSnippet(e.getResponseBodyAsString()));
            return SpeechOutcome.error("AZURE_TTS_FAILED", "Azure TTS failed: HTTP "
                    + e.getStatusCode().value(), PROVIDER_ID);
        } catch (Exception e) {
            log.warn("Azure TTS failed: {}", e.getMessage());
            return SpeechOutcome.error("AZURE_TTS_FAILED", "Azure TTS failed", PROVIDER_ID);
        }
    }

    @Override
    public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
        SpeechOutcome cfg = requireConfigured();
        if (cfg != null) {
            return cfg;
        }
        try {
            byte[] audio = resolveAudioBytes(audioBase64, null);
            String loc = defaultLocale(locale);
            String paramsJson = "{\"ReferenceText\":\"" + escape(referenceText)
                    + "\",\"GradingSystem\":\"HundredMark\",\"Granularity\":\"FullText\",\"Dimension\":\"Comprehensive\"}";
            String pronHeader = Base64.getEncoder().encodeToString(paramsJson.getBytes(StandardCharsets.UTF_8));
            String url = sttUrl(loc);
            String body = webClient.post()
                    .uri(url)
                    .header("Ocp-Apim-Subscription-Key", azure.getKey().trim())
                    .header("Pronunciation-Assessment", pronHeader)
                    .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(audio)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(HTTP_TIMEOUT);
            return mapPronunciationResponse(body, loc, referenceText);
        } catch (WebClientResponseException e) {
            log.warn("Azure pronunciation HTTP {} {}", e.getStatusCode().value(),
                    safeSnippet(e.getResponseBodyAsString()));
            return SpeechOutcome.error("AZURE_PRONUNCIATION_FAILED",
                    "Azure pronunciation failed: HTTP " + e.getStatusCode().value(), PROVIDER_ID);
        } catch (Exception e) {
            log.warn("Azure pronunciation failed: {}", e.getMessage());
            return SpeechOutcome.error("AZURE_PRONUNCIATION_FAILED", "Azure pronunciation failed", PROVIDER_ID);
        }
    }

    private SpeechOutcome requireConfigured() {
        boolean hasOverride = StringUtils.hasText(azure.getSttBaseUrl())
                || StringUtils.hasText(azure.getTtsBaseUrl());
        if (!StringUtils.hasText(azure.getKey())) {
            return SpeechOutcome.error("AZURE_NOT_CONFIGURED",
                    "AZURE_SPEECH_KEY is required when provider=azure", PROVIDER_ID);
        }
        if (!hasOverride && !StringUtils.hasText(azure.getRegion())) {
            return SpeechOutcome.error("AZURE_NOT_CONFIGURED",
                    "AZURE_SPEECH_KEY and AZURE_SPEECH_REGION are required when provider=azure", PROVIDER_ID);
        }
        return null;
    }

    private String sttUrl(String locale) {
        if (StringUtils.hasText(azure.getSttBaseUrl())) {
            String base = azure.getSttBaseUrl().trim();
            String sep = base.contains("?") ? "&" : "?";
            return base + sep + "language=" + locale + "&format=detailed";
        }
        return String.format(Locale.ROOT,
                "https://%s.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=%s&format=detailed",
                azure.getRegion().trim(), locale);
    }

    private String ttsUrl() {
        if (StringUtils.hasText(azure.getTtsBaseUrl())) {
            return azure.getTtsBaseUrl().trim();
        }
        return String.format(Locale.ROOT,
                "https://%s.tts.speech.microsoft.com/cognitiveservices/v1", azure.getRegion().trim());
    }

    private byte[] resolveAudioBytes(String audioBase64, String audioUrl) {
        if (StringUtils.hasText(audioBase64)) {
            return Base64.getDecoder().decode(audioBase64.trim());
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalArgumentException("audio required");
        }
        byte[] bytes = webClient.get()
                .uri(audioUrl.trim())
                .retrieve()
                .bodyToMono(byte[].class)
                .block(URL_FETCH_TIMEOUT);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("empty audio from url");
        }
        return bytes;
    }

    private SpeechOutcome mapAsrResponse(String body, String locale) throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("AZURE_ASR_FAILED", "Empty ASR response", PROVIDER_ID);
        }
        JsonNode root = objectMapper.readTree(body);
        String text = textFromRecognition(root);
        double confidence = confidenceFromRecognition(root);
        return SpeechOutcome.ok("{\"text\":\"" + escape(text) + "\",\"confidence\":" + confidence
                + ",\"locale\":\"" + escape(locale) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    private SpeechOutcome mapPronunciationResponse(String body, String locale, String referenceText)
            throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("AZURE_PRONUNCIATION_FAILED", "Empty pronunciation response", PROVIDER_ID);
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode nbest0 = root.path("NBest").isArray() && root.path("NBest").size() > 0
                ? root.path("NBest").get(0) : root;
        double accuracy = nbest0.path("AccuracyScore").asDouble(
                nbest0.path("PronunciationAssessment").path("AccuracyScore").asDouble(0));
        double fluency = nbest0.path("FluencyScore").asDouble(
                nbest0.path("PronunciationAssessment").path("FluencyScore").asDouble(0));
        double completeness = nbest0.path("CompletenessScore").asDouble(
                nbest0.path("PronunciationAssessment").path("CompletenessScore").asDouble(0));
        double overall = nbest0.path("PronScore").asDouble(
                nbest0.path("PronunciationAssessment").path("PronScore").asDouble(
                        (accuracy + fluency + completeness) / 3.0));
        return SpeechOutcome.ok("{\"overall\":" + overall + ",\"accuracy\":" + accuracy
                + ",\"fluency\":" + fluency + ",\"completeness\":" + completeness
                + ",\"locale\":\"" + escape(locale) + "\",\"referenceText\":\"" + escape(referenceText)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    private static String textFromRecognition(JsonNode root) {
        if (root.hasNonNull("DisplayText")) {
            return root.get("DisplayText").asText("");
        }
        JsonNode nbest = root.path("NBest");
        if (nbest.isArray() && nbest.size() > 0) {
            JsonNode first = nbest.get(0);
            if (first.hasNonNull("Display")) {
                return first.get("Display").asText("");
            }
            if (first.hasNonNull("Lexical")) {
                return first.get("Lexical").asText("");
            }
        }
        return root.path("RecognitionStatus").asText("");
    }

    private static double confidenceFromRecognition(JsonNode root) {
        JsonNode nbest = root.path("NBest");
        if (nbest.isArray() && nbest.size() > 0) {
            return nbest.get(0).path("Confidence").asDouble(0.0);
        }
        return 0.0;
    }

    private String defaultLocale(String locale) {
        if (StringUtils.hasText(locale)) {
            return locale.trim();
        }
        return StringUtils.hasText(azure.getDefaultLocale()) ? azure.getDefaultLocale() : "en-US";
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String xmlEscape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String safeSnippet(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 120 ? body.substring(0, 120) : body;
    }
}
