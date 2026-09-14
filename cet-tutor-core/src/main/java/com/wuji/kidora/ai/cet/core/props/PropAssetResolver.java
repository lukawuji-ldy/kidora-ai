package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.planner.PlanLearningHints;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 将 vocabHints 解析为会话 propAssets（本地库命中才返回）。
 *
 * <p>别名一律来自 {@link PropVocabulary}（即 {@code cet_prop_asset.aliases_json}），
 * 本类不维护任何硬编码词表。</p>
 *
 * @author liudy
 */
@Component
public class PropAssetResolver {

    public static final String PROP_URL_PREFIX = "/api/cet/props/";

    private static final int MAX_HINTS = 6;

    private final CetPropAssetRepository propAssetRepository;
    private final PropVocabulary propVocabulary;

    public PropAssetResolver(CetPropAssetRepository propAssetRepository,
                             PropVocabulary propVocabulary) {
        this.propAssetRepository = propAssetRepository;
        this.propVocabulary = propVocabulary;
    }

    /**
     * 按本课词提示批量解析道具；未命中跳过。
     *
     * @param vocabHints 词提示（≤6）
     * @return propAssets（保持 hint 顺序优先）
     */
    public List<PropAssetView> resolve(List<String> vocabHints) {
        return resolveWithMissing(vocabHints).assets();
    }

    /**
     * 解析已发布道具，并返回需要进入后台生成队列的英文候选词。
     *
     * @param vocabHints 词提示
     * @return 命中资产与缺失 lemma
     */
    public PropResolution resolveWithMissing(List<String> vocabHints) {
        List<String> hints = trimHints(vocabHints);
        if (hints.isEmpty()) {
            return new PropResolution(List.of(), List.of());
        }
        List<String> candidates = candidateLemmas(hints);
        Set<String> tokens = new LinkedHashSet<>();
        for (String hint : hints) {
            tokens.addAll(tokensForHint(hint));
        }
        List<PropAssetRow> rows = propAssetRepository.findActiveMatching(tokens);
        if (rows.isEmpty()) {
            return new PropResolution(List.of(), candidates);
        }
        Map<String, PropAssetRow> byLemma = new LinkedHashMap<>();
        for (PropAssetRow row : rows) {
            if (row.lemma() != null) {
                byLemma.putIfAbsent(row.lemma().toLowerCase(Locale.ROOT), row);
            }
        }
        List<PropAssetView> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String hint : hints) {
            PropAssetRow hit = matchRow(hint, byLemma);
            if (hit == null) {
                continue;
            }
            String lemma = hit.lemma().toLowerCase(Locale.ROOT);
            if (!seen.add(lemma)) {
                continue;
            }
            out.add(new PropAssetView(lemma, nullToDefault(hit.theme()), PROP_URL_PREFIX + lemma));
        }
        List<String> missing = candidates.stream()
                .filter(candidate -> !seen.contains(candidate))
                .toList();
        return new PropResolution(out, missing);
    }

    /**
     * 候选 lemma：已入库的归一到 lemma，未入库的保留原词供生成任务排队。
     */
    private List<String> candidateLemmas(List<String> hints) {
        Set<String> out = new LinkedHashSet<>();
        for (String hint : hints) {
            for (String token : splitTokens(hint)) {
                String canonical = propVocabulary.normalize(token);
                if (canonical != null) {
                    out.add(canonical);
                } else if (token.matches("[a-z]+") && !PlanLearningHints.isPropStopWord(token)) {
                    out.add(token);
                }
            }
        }
        return List.copyOf(out);
    }

    private PropAssetRow matchRow(String hint, Map<String, PropAssetRow> byLemma) {
        for (String token : tokensForHint(hint)) {
            PropAssetRow direct = byLemma.get(token);
            if (direct != null) {
                return direct;
            }
        }
        return null;
    }

    /**
     * hint 展开为检索 token：整串、按非字母切出的词，以及各自的库内归一 lemma。
     */
    Set<String> tokensForHint(String hint) {
        Set<String> tokens = new LinkedHashSet<>();
        if (!StringUtils.hasText(hint)) {
            return tokens;
        }
        for (String token : splitTokens(hint)) {
            tokens.add(token);
            String canonical = propVocabulary.normalize(token);
            if (canonical != null) {
                tokens.add(canonical);
            }
        }
        return tokens;
    }

    /** 整串 + 按非中英文字符切分出的单词，全部小写。 */
    private static List<String> splitTokens(String hint) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(hint)) {
            return out;
        }
        String normalized = hint.trim().toLowerCase(Locale.ROOT);
        out.add(normalized);
        for (String part : normalized.split("[^a-zA-Z\\u4e00-\\u9fff]+")) {
            if (StringUtils.hasText(part) && !out.contains(part)) {
                out.add(part);
            }
        }
        return out;
    }

    private static List<String> trimHints(List<String> vocabHints) {
        if (vocabHints == null || vocabHints.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String h : vocabHints) {
            if (!StringUtils.hasText(h)) {
                continue;
            }
            out.add(h.trim());
            if (out.size() >= MAX_HINTS) {
                break;
            }
        }
        return out;
    }

    private static String nullToDefault(String theme) {
        return StringUtils.hasText(theme) ? theme : "default";
    }

    /**
     * 会话道具视图。
     *
     * @param lemma 词干
     * @param theme 主题
     * @param url   只读 URL
     * @author liudy
     */
    public record PropAssetView(String lemma, String theme, String url) {
    }

    /**
     * 道具解析结果。
     *
     * @param assets        已发布资产
     * @param missingLemmas 尚未有已发布资产的候选 lemma
     * @author liudy
     */
    public record PropResolution(List<PropAssetView> assets, List<String> missingLemmas) {
    }
}
