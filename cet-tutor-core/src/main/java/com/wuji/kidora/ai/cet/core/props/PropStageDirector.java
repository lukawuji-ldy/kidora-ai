package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.props.PropAssetResolver.PropAssetView;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 判定教具舞台：优先采信外教自己声明的道具，否则退回「点名即展示」。
 *
 * <p>判定权在后端，前端只负责渲染。不含任何字幕正则、颜色线索或单一主题（宠物）的
 * 特判——换素材不需要改代码。</p>
 *
 * @author liudy
 */
@Component
public class PropStageDirector {

    /** 舞台布局：人像主视觉。 */
    public static final String LAYOUT_PERSONA_FOCUS = "personaFocus";
    /** 舞台布局：左右分屏，教具为焦点。 */
    public static final String LAYOUT_PROP_FOCUS = "propFocus";

    private static final int MAX_STAGE_ASSETS = 3;

    private final PropVocabulary propVocabulary;

    public PropStageDirector(PropVocabulary propVocabulary) {
        this.propVocabulary = propVocabulary;
    }

    /**
     * 按点名匹配判定本轮舞台（外教未声明道具时的兜底口径）。
     *
     * @param tutorText       外教最终文本（已过输出安全闸门）
     * @param availableAssets 本课已发布且可读的道具
     * @return 舞台视图；未点名任何道具则 personaFocus
     */
    public PropStageView direct(String tutorText, List<PropAssetView> availableAssets) {
        return direct(null, tutorText, availableAssets);
    }

    /**
     * 判定本轮舞台。
     *
     * <p>外教声明的 {@code declaredLemma} 命中本课已发布道具时，它就是主图——教学句里
     * 泛称（pet）常常比被问的具体词（cat / dog）出现得更早，只按字幕顺序排会把泛称推上主图。
     * 声明缺失或不可用时退回按字幕出现顺序排序。</p>
     *
     * @param declaredLemma   外教结构化声明的道具 lemma，可空
     * @param tutorText       外教最终文本（已过输出安全闸门）
     * @param availableAssets 本课已发布且可读的道具
     * @return 舞台视图；既无声明也未点名任何道具则 personaFocus
     */
    public PropStageView direct(String declaredLemma, String tutorText,
                                List<PropAssetView> availableAssets) {
        if (availableAssets == null || availableAssets.isEmpty()) {
            return PropStageView.personaFocus();
        }
        PropAssetView declared = findDeclared(declaredLemma, availableAssets);
        List<PropAssetView> mentioned = mentionedInCaptionOrder(tutorText, availableAssets);
        if (declared == null && mentioned.isEmpty()) {
            return PropStageView.personaFocus();
        }
        List<PropAssetView> ordered = new ArrayList<>();
        if (declared != null) {
            ordered.add(declared);
        }
        for (PropAssetView asset : mentioned) {
            if (ordered.size() >= MAX_STAGE_ASSETS) {
                break;
            }
            if (declared == null || !declared.lemma().equalsIgnoreCase(asset.lemma())) {
                ordered.add(asset);
            }
        }
        return new PropStageView(LAYOUT_PROP_FOCUS, ordered.get(0).lemma(), List.copyOf(ordered));
    }

    /**
     * 声明的 lemma 归一后在本课可用道具里找对应资产。
     *
     * @return 命中的资产；声明为空或不在本课可用集合内返回 null
     */
    private PropAssetView findDeclared(String declaredLemma, List<PropAssetView> availableAssets) {
        if (!StringUtils.hasText(declaredLemma)) {
            return null;
        }
        String raw = declaredLemma.trim().toLowerCase(Locale.ROOT);
        String canonical = propVocabulary.normalize(raw);
        String key = canonical == null ? raw : canonical;
        for (PropAssetView asset : availableAssets) {
            if (asset != null && StringUtils.hasText(asset.lemma())
                    && asset.lemma().equalsIgnoreCase(key)) {
                return asset;
            }
        }
        return null;
    }

    /** 被外教点名的道具，按字幕出现顺序。 */
    private List<PropAssetView> mentionedInCaptionOrder(String tutorText,
                                                        List<PropAssetView> availableAssets) {
        if (!StringUtils.hasText(tutorText)) {
            return List.of();
        }
        String lower = tutorText.toLowerCase(Locale.ROOT);
        List<Mention> mentions = new ArrayList<>();
        for (PropAssetView asset : availableAssets) {
            if (asset == null || !StringUtils.hasText(asset.lemma())) {
                continue;
            }
            int index = firstMentionIndex(lower, propVocabulary.surfaceFormsOf(asset.lemma()));
            if (index >= 0) {
                mentions.add(new Mention(index, asset));
            }
        }
        return mentions.stream()
                .sorted(Comparator.comparingInt(Mention::index))
                .limit(MAX_STAGE_ASSETS)
                .map(Mention::asset)
                .toList();
    }

    /**
     * 文本中任一检索形最早出现的位置；英文按词边界，中文按子串。
     *
     * @return 位置；未出现返回 -1
     */
    private static int firstMentionIndex(String lowerText, Set<String> forms) {
        int best = -1;
        for (String form : forms) {
            if (!StringUtils.hasText(form)) {
                continue;
            }
            int index = form.matches("[a-z0-9 -]+")
                    ? wordBoundaryIndex(lowerText, form)
                    : lowerText.indexOf(form);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    /**
     * 英文按词边界匹配，避免 {@code cat} 命中 {@code caterpillar}；同时容忍常见复数后缀。
     */
    private static int wordBoundaryIndex(String text, String form) {
        int from = 0;
        while (from <= text.length() - form.length()) {
            int index = text.indexOf(form, from);
            if (index < 0) {
                return -1;
            }
            if (isBoundary(text, index - 1) && isWordEnd(text, index + form.length())) {
                return index;
            }
            from = index + 1;
        }
        return -1;
    }

    private static boolean isBoundary(String text, int position) {
        if (position < 0 || position >= text.length()) {
            return true;
        }
        char c = text.charAt(position);
        return !Character.isLetterOrDigit(c);
    }

    /** 词尾允许直接结束，或跟一个复数后缀（cats / boxes）。 */
    private static boolean isWordEnd(String text, int position) {
        if (isBoundary(text, position)) {
            return true;
        }
        if (text.charAt(position) == 's' && isBoundary(text, position + 1)) {
            return true;
        }
        return text.startsWith("es", position) && isBoundary(text, position + 2);
    }

    private record Mention(int index, PropAssetView asset) {
    }

    /**
     * 教具舞台视图。
     *
     * @param layout      personaFocus 或 propFocus
     * @param activeLemma 当前高亮词；personaFocus 时为 null
     * @param assets      本轮展示道具（≤3，按字幕出现顺序）
     * @author liudy
     */
    public record PropStageView(String layout, String activeLemma, List<PropAssetView> assets) {

        static PropStageView personaFocus() {
            return new PropStageView(LAYOUT_PERSONA_FOCUS, null, List.of());
        }

        /** 是否为分屏态。 */
        public boolean isPropFocus() {
            return LAYOUT_PROP_FOCUS.equals(layout);
        }
    }
}
