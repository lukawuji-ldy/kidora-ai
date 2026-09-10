package com.wuji.kidora.ai.mcp.dictionary;

/**
 * 词典供应商抽象。
 *
 * @author liudy
 */
public interface DictionaryProvider {

    /**
     * 供应商标识。
     *
     * @return id
     */
    String providerId();

    /**
     * 查词。
     *
     * @param word   单词
     * @param locale 语言区域，如 en-US
     * @return 结构化 JSON 结果
     */
    DictionaryOutcome lookup(String word, String locale);
}
