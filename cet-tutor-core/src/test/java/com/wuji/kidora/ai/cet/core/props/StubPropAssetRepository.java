package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 无 JDBC 的内存道具库桩，供道具相关单测共用。
 *
 * @author liudy
 */
class StubPropAssetRepository extends CetPropAssetRepository {

    private final List<PropAssetRow> rows;
    private Collection<String> lastTokens = List.of();

    StubPropAssetRepository(List<PropAssetRow> rows) {
        super(null);
        this.rows = rows;
    }

    /** 便捷构造：lemma + 别名 + 主题。 */
    static PropAssetRow row(long id, String lemma, String aliasesJson, String theme) {
        return new PropAssetRow(id, lemma, aliasesJson, theme, theme + "/" + lemma + ".webp",
                "image/webp", 10L, "ACTIVE", null);
    }

    Collection<String> lastTokens() {
        return lastTokens;
    }

    @Override
    public List<PropAssetRow> listActive() {
        return rows;
    }

    @Override
    public List<PropAssetRow> findActiveMatching(Collection<String> tokens) {
        this.lastTokens = tokens == null ? List.of() : List.copyOf(tokens);
        if (tokens == null || tokens.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .filter(r -> tokens.stream().anyMatch(t ->
                        r.lemma().equalsIgnoreCase(t)
                                || (r.aliasesJson() != null
                                && r.aliasesJson().toLowerCase().contains("\"" + t + "\""))))
                .toList();
    }

    @Override
    public Optional<PropAssetRow> findActiveByLemma(String lemma) {
        return rows.stream().filter(r -> r.lemma().equalsIgnoreCase(lemma)).findFirst();
    }
}
