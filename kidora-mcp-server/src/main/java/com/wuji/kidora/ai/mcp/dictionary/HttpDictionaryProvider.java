package com.wuji.kidora.ai.mcp.dictionary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 基于 Free Dictionary API 的 HTTP 词典。
 *
 * @author liudy
 */
public class HttpDictionaryProvider implements DictionaryProvider {

    public static final String PROVIDER_ID = "http";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpDictionaryProvider(WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper,
                                  DictionaryProperties properties) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getHttpBaseUrl();
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public DictionaryOutcome lookup(String word, String locale) {
        if (!StringUtils.hasText(word)) {
            return DictionaryOutcome.error("MISSING_WORD", "word is required", PROVIDER_ID);
        }
        String normalized = word.trim().toLowerCase(Locale.ROOT);
        String encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8);
        String url = baseUrl.endsWith("/") ? baseUrl + encoded : baseUrl + "/" + encoded;
        try {
            String body = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(8));
            return parse(body, normalized);
        } catch (WebClientResponseException.NotFound e) {
            return DictionaryOutcome.error("NOT_FOUND", "word not found: " + normalized, PROVIDER_ID);
        } catch (Exception e) {
            return DictionaryOutcome.error("HTTP_ERROR",
                    e.getMessage() == null ? "dictionary lookup failed" : e.getMessage(),
                    PROVIDER_ID);
        }
    }

    private DictionaryOutcome parse(String body, String word) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!root.isArray() || root.isEmpty()) {
            return DictionaryOutcome.error("NOT_FOUND", "word not found: " + word, PROVIDER_ID);
        }
        JsonNode entry = root.get(0);
        String phonetic = entry.path("phonetic").asText(null);
        if (!StringUtils.hasText(phonetic) && entry.path("phonetics").isArray()) {
            for (JsonNode p : entry.path("phonetics")) {
                if (StringUtils.hasText(p.path("text").asText())) {
                    phonetic = p.path("text").asText();
                    break;
                }
            }
        }
        List<String> definitions = new ArrayList<>();
        List<String> examples = new ArrayList<>();
        for (JsonNode meaning : entry.path("meanings")) {
            for (JsonNode def : meaning.path("definitions")) {
                String d = def.path("definition").asText(null);
                if (StringUtils.hasText(d) && definitions.size() < 5) {
                    definitions.add(d);
                }
                String ex = def.path("example").asText(null);
                if (StringUtils.hasText(ex) && examples.size() < 3) {
                    examples.add(ex);
                }
            }
        }
        return DictionaryOutcome.success(word, phonetic, definitions, examples, PROVIDER_ID);
    }
}
