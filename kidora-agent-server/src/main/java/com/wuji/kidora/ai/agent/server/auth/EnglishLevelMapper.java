package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * 前台「英语水平」档位 → CEFR 映射（后端唯一真相）。
 *
 * @author liudy
 */
public final class EnglishLevelMapper {

    private static final Map<String, String> TO_CEFR = Map.of(
            "BEGINNER", "A0",
            "ELEMENTARY", "A1",
            "INTERMEDIATE", "A2",
            "ADVANCED", "B1"
    );

    private static final Map<String, String> FROM_CEFR = Map.of(
            "A0", "BEGINNER",
            "A1", "ELEMENTARY",
            "A2", "INTERMEDIATE",
            "B1", "ADVANCED"
    );

    private EnglishLevelMapper() {
    }

    /**
     * 将注册档位映射为 CEFR；非法则抛 BAD_REQUEST。
     *
     * @param englishLevel 档位枚举字符串
     * @return CEFR 如 A1
     * @author liudy
     */
    public static String toCefr(String englishLevel) {
        if (!StringUtils.hasText(englishLevel)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "英语水平不能为空");
        }
        String key = englishLevel.trim().toUpperCase(Locale.ROOT);
        String cefr = TO_CEFR.get(key);
        if (cefr == null) {
            throw new KidoraException(ErrorCode.BAD_REQUEST,
                    "英语水平须为 BEGINNER、ELEMENTARY、INTERMEDIATE 或 ADVANCED");
        }
        return cefr;
    }

    /**
     * CEFR → 前台英语水平档位；非法则抛 BAD_REQUEST。
     *
     * @param cefr CEFR 如 A1
     * @return BEGINNER / ELEMENTARY / INTERMEDIATE / ADVANCED
     * @author liudy
     */
    public static String fromCefr(String cefr) {
        if (!StringUtils.hasText(cefr)) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "CEFR 不能为空");
        }
        String key = cefr.trim().toUpperCase(Locale.ROOT);
        String level = FROM_CEFR.get(key);
        if (level == null) {
            throw new KidoraException(ErrorCode.BAD_REQUEST, "无法识别的 CEFR：" + key);
        }
        return level;
    }
}
