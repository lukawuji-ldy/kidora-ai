package com.wuji.kidora.ai.mcp.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * 词典查询结构化结果（稳定 JSON 字段）。
 *
 * @author liudy
 */
public record DictionaryOutcome(String jsonBody) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 成功结果。
     *
     * @param word         词头
     * @param phonetic     音标（可空）
     * @param definitions  释义列表
     * @param examples     例句列表
     * @param provider     供应商标识
     * @return 结果
     */
    public static DictionaryOutcome success(String word, String phonetic,
                                            List<String> definitions, List<String> examples,
                                            String provider) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("word", word == null ? "" : word);
        if (phonetic != null) {
            root.put("phonetic", phonetic);
        } else {
            root.putNull("phonetic");
        }
        ArrayNode defs = root.putArray("definitions");
        if (definitions != null) {
            definitions.forEach(defs::add);
        }
        ArrayNode ex = root.putArray("examples");
        if (examples != null) {
            examples.forEach(ex::add);
        }
        root.put("provider", provider == null ? "stub" : provider);
        return new DictionaryOutcome(root.toString());
    }

    /**
     * 错误结果。
     *
     * @param code     错误码
     * @param message  说明
     * @param provider 供应商标识
     * @return 结果
     */
    public static DictionaryOutcome error(String code, String message, String provider) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode err = root.putObject("error");
        err.put("code", code == null ? "ERROR" : code);
        err.put("message", message == null ? "" : message);
        root.put("provider", provider == null ? "stub" : provider);
        return new DictionaryOutcome(root.toString());
    }
}
