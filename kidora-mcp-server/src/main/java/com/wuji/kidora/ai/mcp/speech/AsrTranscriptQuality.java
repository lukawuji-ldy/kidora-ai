package com.wuji.kidora.ai.mcp.speech;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * ASR 转写质量启发式：locale→引擎映射、弱转写判定、中英结果择优。
 *
 * @author liudy
 */
public final class AsrTranscriptQuality {

    private static final Pattern NON_WORD = Pattern.compile("[\\p{Punct}\\s]+");
    private static final Pattern HAS_HAN = Pattern.compile("\\p{IsHan}");
    private static final Set<String> EN_STOP = Set.of(
            "the", "a", "an", "um", "uh", "oh", "ah", "er", "hm", "hmm",
            "yeah", "yes", "no", "ok", "okay", "mhm", "mm");

    private AsrTranscriptQuality() {
    }

    /**
     * 腾讯 Flash ASR engine_type。
     */
    public static String tencentEngineType(String locale) {
        return isZhLocale(locale) ? "16k_zh" : "16k_en";
    }

    /**
     * 另一腾讯引擎（弱转写回退）。
     */
    public static String otherTencentEngine(String engineType) {
        return "16k_zh".equals(engineType) ? "16k_en" : "16k_zh";
    }

    /**
     * 讯飞 IAT language。
     */
    public static String iFlytekLanguage(String locale) {
        return isZhLocale(locale) ? "zh_cn" : "en_us";
    }

    /**
     * 另一讯飞 language。
     */
    public static String otherIFlytekLanguage(String language) {
        return "zh_cn".equals(language) ? "en_us" : "zh_cn";
    }

    /**
     * 腾讯引擎对应响应 locale。
     */
    public static String localeForTencentEngine(String engineType) {
        return "16k_zh".equals(engineType) ? "zh-CN" : "en-US";
    }

    /**
     * 讯飞 language 对应响应 locale。
     */
    public static String localeForIFlytekLanguage(String language) {
        return "zh_cn".equals(language) ? "zh-CN" : "en-US";
    }

    /**
     * 是否弱/垃圾转写（过短、纯标点、英文功能词）。
     */
    public static boolean isWeak(String text) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        String trimmed = text.trim();
        if (HAS_HAN.matcher(trimmed).find()) {
            return trimmed.replaceAll("\\p{IsHan}", "").replaceAll(NON_WORD.pattern(), "").isEmpty()
                    && countHan(trimmed) < 2;
        }
        String compact = NON_WORD.matcher(trimmed).replaceAll("");
        if (compact.length() <= 2) {
            return true;
        }
        String[] words = trimmed.toLowerCase(Locale.ROOT).split("[\\s\\p{Punct}]+");
        int content = 0;
        for (String w : words) {
            if (!StringUtils.hasText(w)) {
                continue;
            }
            if (!EN_STOP.contains(w)) {
                content++;
            }
        }
        return content == 0;
    }

    /**
     * 在主结果与回退结果间择优：主结果非弱则保留；主弱且回退非弱则取回退；皆弱取较长。
     */
    public static String pickBetter(String primary, String fallback) {
        boolean primaryWeak = isWeak(primary);
        boolean fallbackWeak = isWeak(fallback);
        if (!primaryWeak) {
            return primary == null ? "" : primary.trim();
        }
        if (!fallbackWeak) {
            return fallback == null ? "" : fallback.trim();
        }
        return longer(primary, fallback);
    }

    /**
     * 若回退文本更优则 true（用于决定采用哪次 ASR 的 locale）。
     */
    public static boolean preferFallback(String primary, String fallback) {
        if (!isWeak(primary)) {
            return false;
        }
        String best = pickBetter(primary, fallback);
        String fb = fallback == null ? "" : fallback.trim();
        String p = primary == null ? "" : primary.trim();
        return best.equals(fb) && !fb.equals(p);
    }

    private static boolean isZhLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return false;
        }
        String l = locale.trim().toLowerCase(Locale.ROOT);
        return l.startsWith("zh");
    }

    private static int countHan(String text) {
        int n = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                n++;
            }
            i += Character.charCount(cp);
        }
        return n;
    }

    private static String longer(String a, String b) {
        String x = a == null ? "" : a.trim();
        String y = b == null ? "" : b.trim();
        return y.length() > x.length() ? y : x;
    }
}
