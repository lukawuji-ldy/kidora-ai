package com.wuji.kidora.ai.mcp.dictionary;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 词典 stub（CI / 本地默认）。
 *
 * @author liudy
 */
public class StubDictionaryProvider implements DictionaryProvider {

    public static final String PROVIDER_ID = "stub";

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
        return DictionaryOutcome.success(
                normalized,
                "/" + normalized + "/",
                List.of("A simple word for children: " + normalized + "."),
                List.of("I like the " + normalized + ".", "This is a " + normalized + "."),
                PROVIDER_ID
        );
    }
}
