package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wuji.kidora.ai.cet.core.props.StubPropAssetRepository.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PropAssetResolver 命中 / 未命中 / 库内别名归一。
 *
 * @author liudy
 */
class PropAssetResolverTest {

    private static PropAssetResolver resolverFor(List<PropAssetRow> rows) {
        StubPropAssetRepository repo = new StubPropAssetRepository(rows);
        return new PropAssetResolver(repo, new PropVocabulary(repo, 600L));
    }

    @Test
    void resolve_emptyHints_returnsEmpty() {
        PropAssetResolver resolver = resolverFor(List.of());
        assertTrue(resolver.resolve(List.of()).isEmpty());
        assertTrue(resolver.resolve(null).isEmpty());
    }

    @Test
    void resolve_hitsAliasFromDatabase_returnsPropUrl() {
        StubPropAssetRepository repo = new StubPropAssetRepository(
                List.of(row(1L, "dog", "[\"puppy\",\"狗\"]", "pets")));
        PropAssetResolver resolver = new PropAssetResolver(repo, new PropVocabulary(repo, 600L));

        List<PropAssetResolver.PropAssetView> views = resolver.resolve(List.of("puppy", "unknown"));

        assertEquals(1, views.size());
        assertEquals("dog", views.get(0).lemma());
        assertEquals("pets", views.get(0).theme());
        assertEquals("/api/cet/props/dog", views.get(0).url());
        assertTrue(repo.lastTokens().contains("dog"));
    }

    @Test
    void resolve_unknownHints_omitted() {
        PropAssetResolver resolver = resolverFor(List.of());
        assertTrue(resolver.resolve(List.of("zebra", "unicorn")).isEmpty());
    }

    @Test
    void resolve_preservesHintOrder_andDedups() {
        PropAssetResolver resolver = resolverFor(List.of(
                row(1L, "cat", "[]", "pets"),
                row(2L, "dog", "[]", "pets")));

        List<PropAssetResolver.PropAssetView> views = resolver.resolve(List.of("dog", "cat", "puppy"));

        assertEquals(List.of("dog", "cat"), views.stream()
                .map(PropAssetResolver.PropAssetView::lemma).toList());
    }

    @Test
    void resolve_pluralTopicToken_normalizesToLemma() {
        PropAssetResolver resolver = resolverFor(List.of(
                row(1L, "pet", "[\"宠物\"]", "pets"),
                row(2L, "dog", "[\"狗\"]", "pets")));

        List<PropAssetResolver.PropAssetView> views =
                resolver.resolve(List.of("Animals and pets", "hello", "please"));

        assertEquals(List.of("pet"), views.stream()
                .map(PropAssetResolver.PropAssetView::lemma).toList());
    }

    @Test
    void resolveWithMissing_reportsUnknownNounWithoutReportingTeachingGlue() {
        PropAssetResolver resolver = resolverFor(List.of(row(1L, "dog", "[\"狗\"]", "pets")));

        PropAssetResolver.PropResolution resolution =
                resolver.resolveWithMissing(List.of("rabbit", "dog", "hello", "please"));

        assertEquals(List.of("dog"), resolution.assets().stream()
                .map(PropAssetResolver.PropAssetView::lemma).toList());
        assertEquals(List.of("rabbit"), resolution.missingLemmas());
    }

    @Test
    void resolveWithMissing_emptyLibrary_reportsAllCandidates() {
        PropAssetResolver resolver = resolverFor(List.of());

        PropAssetResolver.PropResolution resolution =
                resolver.resolveWithMissing(List.of("rabbit", "hello"));

        assertTrue(resolution.assets().isEmpty());
        assertEquals(List.of("rabbit"), resolution.missingLemmas());
    }
}
