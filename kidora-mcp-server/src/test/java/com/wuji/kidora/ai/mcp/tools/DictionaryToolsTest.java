package com.wuji.kidora.ai.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.mcp.dictionary.DictionaryProperties;
import com.wuji.kidora.ai.mcp.dictionary.HttpDictionaryProvider;
import com.wuji.kidora.ai.mcp.dictionary.StubDictionaryProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DictionaryTools 契约单测。
 *
 * @author liudy
 */
class DictionaryToolsTest {

    private final DictionaryTools stubTools = new DictionaryTools(new StubDictionaryProvider());

    @Test
    void lookup_stub_success() {
        String json = stubTools.dictionaryLookup("dog", "en-US");
        assertTrue(json.contains("\"word\":\"dog\""));
        assertTrue(json.contains("\"definitions\""));
        assertTrue(json.contains("\"examples\""));
        assertTrue(json.contains("\"provider\":\"stub\""));
    }

    @Test
    void lookup_missingWord_returnsError() {
        String json = stubTools.dictionaryLookup("  ", null);
        assertTrue(json.contains("MISSING_WORD"));
        assertTrue(json.contains("\"error\""));
    }

    @Test
    void lookup_http_parsesDefinitions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setBody("""
                            [{"word":"cat","phonetic":"/kæt/","meanings":[{"definitions":[
                              {"definition":"A small domesticated animal.","example":"I have a cat."}
                            ]}]}]
                            """)
                    .addHeader("Content-Type", "application/json"));
            server.start();
            DictionaryProperties props = new DictionaryProperties();
            props.setHttpBaseUrl(server.url("/api/v2/entries/en").toString().replaceAll("/$", ""));
            HttpDictionaryProvider http = new HttpDictionaryProvider(
                    WebClient.builder(), new ObjectMapper(), props);
            DictionaryTools tools = new DictionaryTools(http);
            String json = tools.dictionaryLookup("cat", "en-US");
            assertTrue(json.contains("\"word\":\"cat\""));
            assertTrue(json.contains("A small domesticated animal"));
            assertTrue(json.contains("I have a cat"));
            assertTrue(json.contains("\"provider\":\"http\""));
        }
    }

    @Test
    void lookup_http_notFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"title\":\"No Definitions Found\"}"));
            server.start();
            DictionaryProperties props = new DictionaryProperties();
            props.setHttpBaseUrl(server.url("/api/v2/entries/en").toString().replaceAll("/$", ""));
            DictionaryTools tools = new DictionaryTools(new HttpDictionaryProvider(
                    WebClient.builder(), new ObjectMapper(), props));
            String json = tools.dictionaryLookup("zzzznotaword", "en-US");
            assertTrue(json.contains("NOT_FOUND"));
        }
    }
}
