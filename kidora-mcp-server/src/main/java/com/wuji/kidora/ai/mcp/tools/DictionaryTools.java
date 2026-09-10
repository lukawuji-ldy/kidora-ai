package com.wuji.kidora.ai.mcp.tools;

import com.wuji.kidora.ai.mcp.dictionary.DictionaryOutcome;
import com.wuji.kidora.ai.mcp.dictionary.DictionaryProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 词典 MCP 工具（委托 {@link DictionaryProvider}）。
 *
 * @author liudy
 */
@Service
public class DictionaryTools {

    private final DictionaryProvider dictionaryProvider;

    public DictionaryTools(DictionaryProvider dictionaryProvider) {
        this.dictionaryProvider = dictionaryProvider;
    }

    /**
     * 查词：释义与例句。
     *
     * @param word   单词
     * @param locale 语言区域
     * @return JSON：word / phonetic / definitions / examples / provider 或 error
     */
    @Tool(name = "dictionary_lookup",
            description = "Look up a word for child-friendly definitions and examples (stub or http)")
    public String dictionaryLookup(
            @ToolParam(description = "Word to look up") String word,
            @ToolParam(description = "Locale e.g. en-US", required = false) String locale) {
        if (!StringUtils.hasText(word)) {
            return DictionaryOutcome.error("MISSING_WORD", "word is required",
                    dictionaryProvider.providerId()).jsonBody();
        }
        return dictionaryProvider.lookup(word, locale).jsonBody();
    }
}
