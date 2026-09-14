package com.wuji.kidora.ai.cet.core.props;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 道具词表：以 {@code cet_prop_asset.aliases_json} 为唯一权威，进程内 TTL 缓存。
 *
 * <p>不含任何硬编码词表；复数等构词变体由通用规则还原，业务别名（puppy/狗/宠物）一律入库。</p>
 *
 * @author liudy
 */
@Component
public class PropVocabulary {

    private static final Logger log = LoggerFactory.getLogger(PropVocabulary.class);

    private final CetPropAssetRepository propAssetRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long ttlNanos;
    private final AtomicReference<Snapshot> cache = new AtomicReference<>(Snapshot.empty());

    public PropVocabulary(CetPropAssetRepository propAssetRepository,
                          @Value("${kidora.cet.props.vocab-ttl-seconds:600}") long ttlSeconds) {
        this.propAssetRepository = propAssetRepository;
        this.ttlNanos = Math.max(1L, ttlSeconds) * 1_000_000_000L;
    }

    /**
     * 归一为规范 lemma：先查库内别名，再退化去复数后重查。
     *
     * @param token 单词或别名（大小写不敏感）
     * @return 规范 lemma；库内无对应则 null
     */
    public String normalize(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        Map<String, String> index = snapshot().aliasToLemma();
        String key = token.trim().toLowerCase(Locale.ROOT);
        String hit = index.get(key);
        if (hit != null) {
            return hit;
        }
        for (String singular : singularCandidates(key)) {
            hit = index.get(singular);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 某 lemma 的全部检索形（含 lemma 本身与库内别名）。
     *
     * @param lemma 规范词干
     * @return 小写检索形；未知 lemma 返回仅含自身的集合
     */
    public Set<String> surfaceFormsOf(String lemma) {
        if (!StringUtils.hasText(lemma)) {
            return Set.of();
        }
        String key = lemma.trim().toLowerCase(Locale.ROOT);
        Set<String> forms = snapshot().lemmaToForms().get(key);
        return forms == null ? Set.of(key) : forms;
    }

    /**
     * 强制下次访问重新加载（导入脚本或管理台发布后可调用）。
     *
     * <p>只把快照标记为过期，保留旧数据作为重载失败时的兜底。</p>
     */
    public void invalidate() {
        cache.updateAndGet(s -> new Snapshot(0L, s.aliasToLemma(), s.lemmaToForms()));
    }

    private Snapshot snapshot() {
        Snapshot current = cache.get();
        if (current.isFresh(ttlNanos)) {
            return current;
        }
        Snapshot loaded = load();
        if (loaded == null) {
            // 加载失败时沿用旧快照，避免道具解析在 DB 抖动期间全线失效。
            return current;
        }
        cache.set(loaded);
        return loaded;
    }

    private Snapshot load() {
        List<PropAssetRow> rows;
        try {
            rows = propAssetRepository.listActive();
        } catch (RuntimeException e) {
            log.warn("CET prop vocabulary reload failed; keeping previous snapshot", e);
            return null;
        }
        Map<String, String> aliasToLemma = new LinkedHashMap<>();
        Map<String, Set<String>> lemmaToForms = new LinkedHashMap<>();
        for (PropAssetRow row : rows) {
            if (!StringUtils.hasText(row.lemma())) {
                continue;
            }
            String lemma = row.lemma().trim().toLowerCase(Locale.ROOT);
            Set<String> forms = lemmaToForms.computeIfAbsent(lemma, k -> new LinkedHashSet<>());
            forms.add(lemma);
            aliasToLemma.putIfAbsent(lemma, lemma);
            for (String alias : parseAliases(row.aliasesJson())) {
                forms.add(alias);
                aliasToLemma.putIfAbsent(alias, lemma);
            }
        }
        return new Snapshot(System.nanoTime(), Map.copyOf(aliasToLemma), Map.copyOf(lemmaToForms));
    }

    private Set<String> parseAliases(String aliasesJson) {
        if (!StringUtils.hasText(aliasesJson)) {
            return Set.of();
        }
        try {
            JsonNode node = objectMapper.readTree(aliasesJson);
            if (!node.isArray()) {
                return Set.of();
            }
            Set<String> out = new LinkedHashSet<>();
            for (JsonNode item : node) {
                String alias = item.asText("").trim().toLowerCase(Locale.ROOT);
                if (StringUtils.hasText(alias)) {
                    out.add(alias);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("CET prop aliases_json parse failed: {}", aliasesJson, e);
            return Set.of();
        }
    }

    /** 通用英文复数还原（cats→cat、boxes→box、babies→baby）。 */
    static List<String> singularCandidates(String token) {
        if (token.length() < 3 || !token.matches("[a-z]+")) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>(3);
        if (token.endsWith("ies")) {
            out.add(token.substring(0, token.length() - 3) + "y");
        }
        if (token.endsWith("es")) {
            out.add(token.substring(0, token.length() - 2));
        }
        if (token.endsWith("s")) {
            out.add(token.substring(0, token.length() - 1));
        }
        return out;
    }

    /**
     * 词表快照。
     *
     * @param loadedAtNanos 加载时刻
     * @param aliasToLemma  别名（含 lemma 自身）→ lemma
     * @param lemmaToForms  lemma → 全部检索形
     * @author liudy
     */
    private record Snapshot(long loadedAtNanos, Map<String, String> aliasToLemma,
                            Map<String, Set<String>> lemmaToForms) {

        static Snapshot empty() {
            return new Snapshot(0L, Map.of(), Map.of());
        }

        boolean isFresh(long ttlNanos) {
            return loadedAtNanos != 0L && System.nanoTime() - loadedAtNanos < ttlNanos;
        }
    }
}
