package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AzureSpeechProvider REST 映射单测（MockWebServer + stt/tts base-url 覆盖）。
 *
 * @author liudy
 */
class AzureSpeechProviderTest {

    private MockWebServer server;

    private AzureSpeechProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String base = server.url("/").toString().replaceAll("/$", "");
        SpeechProperties.Azure azure = new SpeechProperties.Azure();
        azure.setKey("test-key");
        azure.setRegion("eastus");
        azure.setSttBaseUrl(base + "/stt");
        azure.setTtsBaseUrl(base + "/tts");
        provider = new AzureSpeechProvider(azure, WebClient.builder().build(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void transcribe_mapsDisplayText() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"RecognitionStatus\":\"Success\",\"DisplayText\":\"Hi there.\",\"NBest\":[{\"Confidence\":0.91}]}")
                .addHeader("Content-Type", "application/json"));
        SpeechOutcome out = provider.transcribe(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), null, "en-US");
        assertTrue(out.success(), out.jsonBody());
        assertTrue(out.jsonBody().contains("\"text\":\"Hi there.\""));
        assertTrue(out.jsonBody().contains("\"provider\":\"azure\""));
        assertTrue(out.jsonBody().contains("0.91"));
        RecordedRequest req = server.takeRequest();
        assertEquals("test-key", req.getHeader("Ocp-Apim-Subscription-Key"));
        assertTrue(req.getPath().contains("language=en-US"));
    }

    @Test
    void synthesize_returnsBase64Wav() throws Exception {
        byte[] wav = "RIFF....WAVE".getBytes(StandardCharsets.US_ASCII);
        server.enqueue(new MockResponse().setBody(new Buffer().write(wav)));
        SpeechOutcome out = provider.synthesize("Hello", "en-US-AvaNeural", "en-US");
        assertTrue(out.success(), out.jsonBody());
        assertTrue(out.jsonBody().contains("\"mimeType\":\"audio/wav\""));
        assertTrue(out.jsonBody().contains(Base64.getEncoder().encodeToString(wav)));
        assertTrue(out.jsonBody().contains("\"provider\":\"azure\""));
    }

    @Test
    void pronunciation_mapsScores() throws Exception {
        String body = "{\"NBest\":[{\"AccuracyScore\":90.0,\"FluencyScore\":80.0,\"CompletenessScore\":70.0,\"PronScore\":85.0}]}";
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));
        SpeechOutcome out = provider.scorePronunciation(
                Base64.getEncoder().encodeToString(new byte[]{9}), "Hello.", "en-US");
        assertTrue(out.success(), out.jsonBody());
        assertTrue(out.jsonBody().contains("\"overall\":85.0"));
        assertTrue(out.jsonBody().contains("\"accuracy\":90.0"));
        assertTrue(out.jsonBody().contains("\"provider\":\"azure\""));
        RecordedRequest req = server.takeRequest();
        assertTrue(req.getHeader("Pronunciation-Assessment") != null);
    }

    @Test
    void notConfigured_withoutKey() {
        SpeechProperties.Azure azure = new SpeechProperties.Azure();
        AzureSpeechProvider empty = new AzureSpeechProvider(azure, WebClient.builder().build(), new ObjectMapper());
        SpeechOutcome out = empty.transcribe("AAAA", null, "en-US");
        assertTrue(out.jsonBody().contains("AZURE_NOT_CONFIGURED"));
    }
}
