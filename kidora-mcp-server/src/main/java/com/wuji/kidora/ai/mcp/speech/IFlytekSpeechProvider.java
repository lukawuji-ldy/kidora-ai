package com.wuji.kidora.ai.mcp.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wuji.kidora.ai.common.crypto.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞整栈：听写 ASR + 合成 TTS + ISE 发音（英文句子）。
 * <p>
 * 走控制台流式 WebSocket（听写 / 在线合成 / 评测）；旧版 HTTP
 * {@code api.xfyun.cn/v1/service/v1/*} 已不对新应用开放。鉴权与管理台
 * {@code IflytekWsAuth} 一致：HMAC-SHA256（apiKey + apiSecret）。
 *
 * @author liudy
 */
public class IFlytekSpeechProvider implements SpeechProvider {

    public static final String PROVIDER_ID = SpeechVendorCodes.IFLYTEK;

    private static final Logger log = LoggerFactory.getLogger(IFlytekSpeechProvider.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final int WS_CONNECT_SECONDS = 20;
    private static final int WS_DONE_SECONDS = 45;
    private static final int AUDIO_CHUNK = 1280;

    private static final String IAT_HOST = "iat-api.xfyun.cn";
    private static final String IAT_PATH = "/v2/iat";
    private static final String TTS_HOST = "tts-api.xfyun.cn";
    private static final String TTS_PATH = "/v2/tts";
    private static final String ISE_HOST = "ise-api.xfyun.cn";
    private static final String ISE_PATH = "/v2/open-ise";

    private static final DateTimeFormatter RFC1123 = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final SpeechVendorRepository vendorRepository;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final HttpClient httpClient;

    public IFlytekSpeechProvider(SpeechVendorRepository vendorRepository,
                                 SecretCipher secretCipher,
                                 ObjectMapper objectMapper,
                                 WebClient.Builder webClientBuilder) {
        this(vendorRepository, secretCipher, objectMapper, webClientBuilder.build());
    }

    /** 测试注入 */
    public IFlytekSpeechProvider(SpeechVendorRepository vendorRepository,
                                 SecretCipher secretCipher,
                                 ObjectMapper objectMapper,
                                 WebClient webClient) {
        this.vendorRepository = vendorRepository;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
        this.webClient = webClient;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public SpeechOutcome transcribe(String audioBase64, String audioUrl, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            byte[] raw = resolveAudio(audioBase64, audioUrl);
            if (isM4a(raw)) {
                return SpeechOutcome.error("INVALID_AUDIO",
                        "iFlytek ASR requires wav/pcm/mp3; do not upload m4a", PROVIDER_ID);
            }
            String encoding = iatEncoding(raw);
            byte[] audio = "lame".equals(encoding) ? raw : toPcmOrRaw(raw);
            VendorCredentials c = creds.get();
            String url = buildWssUrl(IAT_HOST, IAT_PATH, c.get("apiKey"), c.get("apiSecret"));
            String primaryLang = AsrTranscriptQuality.iFlytekLanguage(locale);
            String primaryText = iatRecognize(c.get("appId"), url, audio, encoding, primaryLang);
            String primaryLocale = AsrTranscriptQuality.localeForIFlytekLanguage(primaryLang);
            if (!AsrTranscriptQuality.isWeak(primaryText)) {
                return asrOk(primaryText, primaryLocale);
            }
            String secondaryLang = AsrTranscriptQuality.otherIFlytekLanguage(primaryLang);
            String secondaryUrl = buildWssUrl(IAT_HOST, IAT_PATH, c.get("apiKey"), c.get("apiSecret"));
            try {
                String secondaryText = iatRecognize(c.get("appId"), secondaryUrl, audio, encoding, secondaryLang);
                if (AsrTranscriptQuality.preferFallback(primaryText, secondaryText)) {
                    return asrOk(secondaryText, AsrTranscriptQuality.localeForIFlytekLanguage(secondaryLang));
                }
            } catch (Exception fallbackEx) {
                log.warn("iFlytek ASR zh/en fallback failed: {}", fallbackEx.getMessage());
            }
            return asrOk(primaryText, primaryLocale);
        } catch (Exception e) {
            log.warn("iFlytek ASR failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED",
                    StringUtils.hasText(e.getMessage()) ? e.getMessage() : "iFlytek ASR failed", PROVIDER_ID);
        }
    }

    private SpeechOutcome asrOk(String text, String locale) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"text\":\"" + escape(text) + "\",\"confidence\":0.9,\"locale\":\""
                + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    @Override
    public SpeechOutcome synthesize(String text, String voice, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            VendorCredentials c = creds.get();
            String vcn = StringUtils.hasText(voice) ? voice.trim() : "x4_xiaoyan";
            String url = buildWssUrl(TTS_HOST, TTS_PATH, c.get("apiKey"), c.get("apiSecret"));
            byte[] audio = ttsSynthesize(c.get("appId"), url, text, vcn);
            if (audio == null || audio.length == 0) {
                return SpeechOutcome.error("VENDOR_API_FAILED", "Empty TTS body", PROVIDER_ID);
            }
            // 流式 lame 拼帧后末帧可能不完整，浏览器会 PIPELINE_ERROR_DECODE 掐掉尾句
            audio = Mp3AudioTail.trimIncompleteTrailingFrame(audio);
            String loc = defaultLocale(locale);
            return SpeechOutcome.ok("{\"audioBase64\":\"" + Base64.getEncoder().encodeToString(audio)
                    + "\",\"mimeType\":\"audio/mpeg\",\"voice\":\"" + escape(vcn)
                    + "\",\"locale\":\"" + escape(loc) + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
        } catch (Exception e) {
            log.warn("iFlytek TTS failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED",
                    StringUtils.hasText(e.getMessage()) ? e.getMessage() : "iFlytek TTS failed", PROVIDER_ID);
        }
    }

    @Override
    public SpeechOutcome scorePronunciation(String audioBase64, String referenceText, String locale) {
        Optional<VendorCredentials> creds = loadCreds();
        if (creds.isEmpty()) {
            return notConfigured();
        }
        try {
            byte[] raw = resolveAudio(audioBase64, null);
            if (isM4a(raw)) {
                return SpeechOutcome.error("INVALID_AUDIO",
                        "iFlytek ISE requires wav/pcm/mp3; do not upload m4a", PROVIDER_ID);
            }
            String aue = isMp3(raw) ? "lame" : "raw";
            byte[] pcm = "lame".equals(aue) ? raw : toPcmOrRaw(raw);
            VendorCredentials c = creds.get();
            String url = buildWssUrl(ISE_HOST, ISE_PATH, c.get("apiKey"), c.get("apiSecret"));
            return iseEvaluate(c.get("appId"), url, pcm, referenceText, locale, aue);
        } catch (Exception e) {
            log.warn("iFlytek ISE failed: {}", e.getMessage());
            return SpeechOutcome.error("VENDOR_API_FAILED",
                    StringUtils.hasText(e.getMessage()) ? e.getMessage() : "iFlytek ISE failed", PROVIDER_ID);
        }
    }

    private String iatRecognize(String appId, String wssUrl, byte[] audio, String encoding, String language)
            throws Exception {
        CompletableFuture<String> done = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();
        String enc = StringUtils.hasText(encoding) ? encoding : "raw";
        String lang = StringUtils.hasText(language) ? language : "en_us";
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder frameBuf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                try {
                    int offset = 0;
                    boolean first = true;
                    do {
                        int end = Math.min(offset + AUDIO_CHUNK, audio.length);
                        byte[] part = offset >= audio.length
                                ? new byte[0]
                                : Arrays.copyOfRange(audio, offset, end);
                        offset = end;
                        int st = first ? (offset >= audio.length ? 2 : 0) : (offset >= audio.length ? 2 : 1);
                        first = false;
                        ObjectNode frame = objectMapper.createObjectNode();
                        if (st == 0 || (st == 2 && audio.length <= AUDIO_CHUNK)) {
                            frame.set("common", objectMapper.createObjectNode().put("app_id", appId));
                            frame.set("business", objectMapper.createObjectNode()
                                    .put("language", lang)
                                    .put("domain", "iat")
                                    .put("accent", "mandarin")
                                    .put("vad_eos", 3000));
                        }
                        frame.set("data", objectMapper.createObjectNode()
                                .put("status", st)
                                .put("format", "audio/L16;rate=16000")
                                .put("encoding", enc)
                                .put("audio", Base64.getEncoder().encodeToString(part)));
                        webSocket.sendText(objectMapper.writeValueAsString(frame), true);
                        if (st == 2) {
                            break;
                        }
                    } while (offset < audio.length || first);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                webSocket.request(1);
                Optional<String> msg = accumulateWsText(frameBuf, data, last);
                if (msg.isEmpty()) {
                    return null;
                }
                try {
                    JsonNode root = objectMapper.readTree(msg.get());
                    int code = root.path("code").asInt(0);
                    if (code != 0) {
                        done.completeExceptionally(new IllegalStateException(
                                root.path("message").asText("ASR error code=" + code)));
                        return null;
                    }
                    JsonNode ws = root.path("data").path("result").path("ws");
                    if (ws.isArray()) {
                        for (JsonNode w : ws) {
                            JsonNode cw = w.path("cw");
                            if (cw.isArray()) {
                                for (JsonNode c : cw) {
                                    text.append(c.path("w").asText(""));
                                }
                            }
                        }
                    }
                    if (root.path("data").path("status").asInt(-1) == 2) {
                        done.complete(text.toString());
                    }
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }
        };
        httpClient.newWebSocketBuilder().buildAsync(URI.create(wssUrl), listener)
                .get(WS_CONNECT_SECONDS, TimeUnit.SECONDS);
        return done.get(WS_DONE_SECONDS, TimeUnit.SECONDS);
    }

    private byte[] ttsSynthesize(String appId, String wssUrl, String text, String vcn) throws Exception {
        CompletableFuture<byte[]> done = new CompletableFuture<>();
        java.io.ByteArrayOutputStream audioOut = new java.io.ByteArrayOutputStream();
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder frameBuf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                try {
                    ObjectNode frame = objectMapper.createObjectNode();
                    frame.set("common", objectMapper.createObjectNode().put("app_id", appId));
                    frame.set("business", objectMapper.createObjectNode()
                            .put("aue", "lame")
                            .put("auf", "audio/L16;rate=16000")
                            .put("vcn", vcn)
                            .put("tte", "UTF8")
                            .put("sfl", 1));
                    frame.set("data", objectMapper.createObjectNode()
                            .put("status", 2)
                            .put("text", Base64.getEncoder().encodeToString(
                                    text.getBytes(StandardCharsets.UTF_8))));
                    webSocket.sendText(objectMapper.writeValueAsString(frame), true);
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                webSocket.request(1);
                Optional<String> msg = accumulateWsText(frameBuf, data, last);
                if (msg.isEmpty()) {
                    return null;
                }
                try {
                    JsonNode root = objectMapper.readTree(msg.get());
                    int code = root.path("code").asInt(0);
                    if (code != 0) {
                        String err = root.path("message").asText("");
                        if (!StringUtils.hasText(err)) {
                            err = root.path("desc").asText("TTS error code=" + code);
                        }
                        done.completeExceptionally(new IllegalStateException(
                                StringUtils.hasText(err) ? "code=" + code + ", " + err : "TTS error code=" + code));
                        return null;
                    }
                    String chunk = root.path("data").path("audio").asText("");
                    if (StringUtils.hasText(chunk)) {
                        decodeTtsAudioChunk(audioOut, chunk);
                    }
                    if (root.path("data").path("status").asInt(-1) == 2) {
                        done.complete(audioOut.toByteArray());
                    }
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }
        };
        httpClient.newWebSocketBuilder().buildAsync(URI.create(wssUrl), listener)
                .get(WS_CONNECT_SECONDS, TimeUnit.SECONDS);
        return done.get(WS_DONE_SECONDS, TimeUnit.SECONDS);
    }

    private SpeechOutcome iseEvaluate(String appId, String wssUrl, byte[] pcm,
                                      String referenceText, String locale, String aue) throws Exception {
        CompletableFuture<SpeechOutcome> done = new CompletableFuture<>();
        String audioEnc = StringUtils.hasText(aue) ? aue : "raw";
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder frameBuf = new StringBuilder();

            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
                try {
                    ObjectNode ssb = objectMapper.createObjectNode();
                    ssb.set("common", objectMapper.createObjectNode().put("app_id", appId));
                    ssb.set("business", objectMapper.createObjectNode()
                            .put("sub", "ise")
                            .put("ent", "en_vip")
                            .put("category", "read_sentence")
                            .put("cmd", "ssb")
                            .put("auf", "audio/L16;rate=16000")
                            .put("aue", audioEnc)
                            .put("text", "\uFEFF" + (referenceText == null ? "" : referenceText))
                            .put("ttp_skip", true)
                            .put("rstcd", "utf8"));
                    ssb.set("data", objectMapper.createObjectNode().put("status", 0));
                    webSocket.sendText(objectMapper.writeValueAsString(ssb), true);

                    int offset = 0;
                    boolean first = true;
                    while (offset < pcm.length) {
                        int end = Math.min(offset + AUDIO_CHUNK, pcm.length);
                        byte[] part = Arrays.copyOfRange(pcm, offset, end);
                        offset = end;
                        int aus = first ? 1 : (offset >= pcm.length ? 4 : 2);
                        first = false;
                        ObjectNode auw = objectMapper.createObjectNode();
                        auw.set("business", objectMapper.createObjectNode()
                                .put("cmd", "auw")
                                .put("aus", aus)
                                .put("aue", audioEnc));
                        auw.set("data", objectMapper.createObjectNode()
                                .put("status", offset >= pcm.length ? 2 : 1)
                                .put("data", Base64.getEncoder().encodeToString(part)));
                        webSocket.sendText(objectMapper.writeValueAsString(auw), true);
                    }
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                webSocket.request(1);
                Optional<String> msg = accumulateWsText(frameBuf, data, last);
                if (msg.isEmpty()) {
                    return null;
                }
                try {
                    JsonNode root = objectMapper.readTree(msg.get());
                    int code = root.path("code").asInt(0);
                    if (code != 0) {
                        done.complete(SpeechOutcome.error("VENDOR_API_FAILED",
                                root.path("message").asText("ISE error code=" + code), PROVIDER_ID));
                        return null;
                    }
                    String dataField = root.path("data").path("data").asText("");
                    if (StringUtils.hasText(dataField) && root.path("data").path("status").asInt(-1) == 2) {
                        done.complete(mapIsePayload(dataField, locale, referenceText));
                    }
                } catch (Exception e) {
                    done.completeExceptionally(e);
                }
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                done.completeExceptionally(error);
            }
        };
        httpClient.newWebSocketBuilder().buildAsync(URI.create(wssUrl), listener)
                .get(WS_CONNECT_SECONDS, TimeUnit.SECONDS);
        return done.get(WS_DONE_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 拼装 JDK HttpClient WebSocket 文本分片：仅当 {@code last=true} 时返回完整消息。
     * <p>
     * TTS/ISE 回包常含大段 base64，约 4KB 处被拆帧；未拼帧直接 {@code readTree} 会触发
     * {@code JsonEOFException}（与腾讯 SOE 侧 {@code buf}/{@code last} 处理一致）。
     */
    static Optional<String> accumulateWsText(StringBuilder buf, CharSequence data, boolean last) {
        buf.append(data);
        if (!last) {
            return Optional.empty();
        }
        String msg = buf.toString();
        buf.setLength(0);
        return Optional.of(msg);
    }

    /**
     * 讯飞 TTS 每帧 {@code data.audio} 为独立 base64，须逐帧 decode 后拼二进制（官方 demo 同）。
     * 不可把多帧 base64 字符串拼成一串再 decode（中间 padding {@code =} 会触发
     * {@code Incorrect ending byte}）。
     */
    static void decodeTtsAudioChunk(java.io.ByteArrayOutputStream out, String chunkB64) {
        if (!StringUtils.hasText(chunkB64)) {
            return;
        }
        try {
            out.write(Base64.getDecoder().decode(chunkB64.trim()));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("TTS audio buffer write failed", e);
        }
    }

    /**
     * 映射流式 ISE 最终 data（base64 XML/JSON）到统一分数字段。
     */
    SpeechOutcome mapIsePayload(String dataField, String locale, String referenceText) throws Exception {
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(dataField), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            decoded = dataField;
        }
        return mapIse(decoded, locale, referenceText);
    }

    /**
     * 映射 ISE XML/JSON 结果中的总分维度（可单测）。
     */
    static SpeechOutcome mapIseScores(double overall, double accuracy, double fluency, double completeness,
                                      String locale, String referenceText) {
        String loc = defaultLocale(locale);
        return SpeechOutcome.ok("{\"overall\":" + overall + ",\"accuracy\":" + accuracy
                + ",\"fluency\":" + fluency + ",\"completeness\":" + completeness
                + ",\"locale\":\"" + escape(loc) + "\",\"referenceText\":\"" + escape(referenceText)
                + "\",\"provider\":\"" + PROVIDER_ID + "\"}");
    }

    SpeechOutcome mapIse(String body, String locale, String referenceText) throws Exception {
        if (!StringUtils.hasText(body)) {
            return SpeechOutcome.error("VENDOR_API_FAILED", "Empty ISE", PROVIDER_ID);
        }
        if (body.trim().startsWith("{")) {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("code") && root.path("code").asInt() != 0) {
                return SpeechOutcome.error("VENDOR_API_FAILED", root.path("message").asText(
                        root.path("desc").asText("ISE error")), PROVIDER_ID);
            }
            JsonNode data = root.path("data");
            JsonNode readChapter = root.path("read_sentence").path("rec_paper").path("read_chapter");
            if (readChapter.isMissingNode()) {
                readChapter = root.path("read_chapter");
            }
            double overall = firstDouble(readChapter, "total_score", "overall");
            double accuracy = firstDouble(readChapter, "accuracy_score", "accuracy");
            double fluency = firstDouble(readChapter, "fluency_score", "fluency");
            double completeness = firstDouble(readChapter, "integrity_score", "completeness");
            if (overall == 0 && accuracy == 0) {
                overall = firstDouble(data, "total_score", "overall");
                accuracy = firstDouble(data, "accuracy_score", "accuracy");
                fluency = firstDouble(data, "fluency_score", "fluency");
                completeness = firstDouble(data, "integrity_score", "completeness");
            }
            if (overall == 0 && accuracy == 0) {
                overall = extractXmlScore(body, "total_score");
                accuracy = extractXmlScore(body, "accuracy_score");
                fluency = extractXmlScore(body, "fluency_score");
                completeness = extractXmlScore(body, "integrity_score");
            }
            return mapIseScores(overall, accuracy, fluency, completeness, locale, referenceText);
        }
        double overall = extractXmlScore(body, "total_score");
        double accuracy = extractXmlScore(body, "accuracy_score");
        double fluency = extractXmlScore(body, "fluency_score");
        double completeness = extractXmlScore(body, "integrity_score");
        return mapIseScores(overall, accuracy, fluency, completeness, locale, referenceText);
    }

    /**
     * HMAC-SHA256 WebSocket 握手 URL（与管理台 IflytekWsAuth 一致）。
     */
    static String buildWssUrl(String host, String path, String apiKey, String apiSecret) {
        return buildWssUrl(host, path, apiKey, apiSecret, RFC1123.format(ZonedDateTime.now(ZoneOffset.UTC)));
    }

    static String buildWssUrl(String host, String path, String apiKey, String apiSecret, String date) {
        try {
            String requestLine = "GET " + path + " HTTP/1.1";
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder()
                    .encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
            String authorizationOrigin = "api_key=\"" + apiKey
                    + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\""
                    + signature + "\"";
            String authorization = Base64.getEncoder()
                    .encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            return "wss://" + host + path
                    + "?authorization=" + URLEncoder.encode(authorization, StandardCharsets.UTF_8)
                    + "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8)
                    + "&host=" + URLEncoder.encode(host, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("iFlytek WS auth failed: " + e.getMessage(), e);
        }
    }

    static byte[] toPcmOrRaw(byte[] audio) {
        if (isWav(audio) && audio.length > 44) {
            byte[] pcm = new byte[audio.length - 44];
            System.arraycopy(audio, 44, pcm, 0, pcm.length);
            return pcm;
        }
        return audio;
    }

    static boolean isWav(byte[] audio) {
        return audio != null && audio.length >= 12
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }

    static boolean isM4a(byte[] audio) {
        return audio != null && audio.length >= 8
                && audio[4] == 'f' && audio[5] == 't' && audio[6] == 'y' && audio[7] == 'p';
    }

    /** ID3 或 MPEG 帧同步字头。 */
    static boolean isMp3(byte[] audio) {
        if (audio == null || audio.length < 3) {
            return false;
        }
        if (audio[0] == 'I' && audio[1] == 'D' && audio[2] == '3') {
            return true;
        }
        return (audio[0] & 0xFF) == 0xFF && (audio[1] & 0xE0) == 0xE0;
    }

    /** 讯飞听写 encoding：mp3 → lame，其余 raw（pcm）。 */
    static String iatEncoding(byte[] audio) {
        return isMp3(audio) ? "lame" : "raw";
    }

    private Optional<VendorCredentials> loadCreds() {
        if (vendorRepository == null) {
            return Optional.empty();
        }
        return vendorRepository.findByCode(PROVIDER_ID)
                .filter(SpeechVendorRecord::active)
                .flatMap(r -> VendorCredentials.parse(r.credentialsCipher(), secretCipher, objectMapper))
                .filter(c -> c.hasText("appId") && c.hasText("apiKey") && c.hasText("apiSecret"));
    }

    private SpeechOutcome notConfigured() {
        return SpeechOutcome.error("VENDOR_NOT_CONFIGURED",
                "iFlytek credentials missing in speech_vendor_config (need appId+apiKey+apiSecret)", PROVIDER_ID);
    }

    private byte[] resolveAudio(String audioBase64, String audioUrl) {
        if (StringUtils.hasText(audioBase64)) {
            return Base64.getDecoder().decode(audioBase64.trim());
        }
        if (!StringUtils.hasText(audioUrl)) {
            throw new IllegalArgumentException("audio required");
        }
        byte[] bytes = webClient.get().uri(audioUrl.trim()).retrieve().bodyToMono(byte[].class).block(TIMEOUT);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("empty audio url");
        }
        return bytes;
    }

    private static double firstDouble(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode()) {
            return 0;
        }
        for (String k : keys) {
            if (node.has(k)) {
                return node.path(k).asDouble(0);
            }
        }
        return 0;
    }

    static double extractXmlScore(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int i = xml.indexOf(open);
        if (i < 0) {
            String attr = tag + "=\"";
            int a = xml.indexOf(attr);
            if (a < 0) {
                return 0;
            }
            int s = a + attr.length();
            int e = xml.indexOf('"', s);
            if (e < 0) {
                return 0;
            }
            try {
                return Double.parseDouble(xml.substring(s, e));
            } catch (NumberFormatException ex) {
                return 0;
            }
        }
        int s = i + open.length();
        int e = xml.indexOf(close, s);
        if (e < 0) {
            return 0;
        }
        try {
            return Double.parseDouble(xml.substring(s, e).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
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
