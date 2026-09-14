package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.props.PropAssetResolver.PropAssetView;
import com.wuji.kidora.ai.cet.core.props.PropStageDirector.PropStageView;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.wuji.kidora.ai.cet.core.props.StubPropAssetRepository.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 舞台判定：外教点名本课已发布道具才分屏，其余一律人像态。
 *
 * <p>覆盖原 {@code kidora-web/src/lib/lessonProps.selftest.ts} 的全部场景。</p>
 *
 * @author liudy
 */
class PropStageDirectorTest {

    private static final List<PropAssetRow> PET_LIBRARY = List.of(
            row(1L, "dog", "[\"puppy\",\"狗\",\"小狗\"]", "pets"),
            row(2L, "cat", "[\"kitten\",\"猫\",\"小猫\"]", "pets"),
            row(3L, "pet", "[\"pets\",\"animal\",\"animals\",\"宠物\"]", "pets"));

    private static final List<PropAssetView> PET_ASSETS = List.of(
            new PropAssetView("dog", "pets", "/api/cet/props/dog"),
            new PropAssetView("cat", "pets", "/api/cet/props/cat"),
            new PropAssetView("pet", "pets", "/api/cet/props/pet"));

    private static PropStageDirector director() {
        return director(PET_LIBRARY);
    }

    private static PropStageDirector director(List<PropAssetRow> rows) {
        StubPropAssetRepository repo = new StubPropAssetRepository(rows);
        return new PropStageDirector(new PropVocabulary(repo, 600L));
    }

    @Test
    void namesTwoEntities_splitsInCaptionOrder() {
        PropStageView view = director().direct("Look! A dog and a cat.", PET_ASSETS);

        assertTrue(view.isPropFocus());
        assertEquals("dog", view.activeLemma());
        assertEquals(List.of("dog", "cat"), lemmas(view));
    }

    @Test
    void captionOrderWins_notAssetListOrder() {
        PropStageView view = director().direct("Look! A cat and a dog.", PET_ASSETS);

        assertEquals("cat", view.activeLemma());
        assertEquals(List.of("cat", "dog"), lemmas(view));
    }

    @Test
    void pointingQuestion_activatesNamedEntity() {
        PropStageView view = director().direct("Great job! Which one is the cat?", PET_ASSETS);

        assertTrue(view.isPropFocus());
        assertEquals("cat", view.activeLemma());
    }

    @Test
    void praiseWithoutProp_collapsesToPersonaFocus() {
        PropStageView view = director().direct("Nice job! Say it again.", PET_ASSETS);

        assertEquals(PropStageDirector.LAYOUT_PERSONA_FOCUS, view.layout());
        assertNull(view.activeLemma());
        assertTrue(view.assets().isEmpty());
    }

    @Test
    void emptyLibrary_collapsesToPersonaFocus() {
        PropStageView view = director().direct("Look! A dog!", List.of());

        assertFalse(view.isPropFocus());
    }

    @Test
    void blankTutorText_collapsesToPersonaFocus() {
        assertFalse(director().direct("", PET_ASSETS).isPropFocus());
        assertFalse(director().direct(null, PET_ASSETS).isPropFocus());
    }

    @Test
    void databaseAlias_matchesEnglishAndChinese() {
        assertEquals("dog", director().direct("Look at the puppy!", PET_ASSETS).activeLemma());
        assertEquals("dog", director().direct("看看这只小狗！", PET_ASSETS).activeLemma());
        assertEquals("cat", director().direct("这是一只猫。", PET_ASSETS).activeLemma());
    }

    @Test
    void pluralForm_matchesLemma() {
        assertEquals("dog", director().direct("Look at the dogs!", PET_ASSETS).activeLemma());
    }

    @Test
    void wordBoundary_doesNotMatchSubstring() {
        PropStageView view = director().direct("Look at the caterpillar!", PET_ASSETS);

        assertFalse(view.isPropFocus(), "cat 不应命中 caterpillar");
    }

    @Test
    void genericPetCaption_showsGenericAsset() {
        PropStageView view = director().direct("Look! These are pets.", PET_ASSETS);

        assertEquals("pet", view.activeLemma());
    }

    @Test
    void attributeQuestion_hasNoColorSpecialCase() {
        // 旧前端会把 yellow 特判成 dog；后端只认外教实际点名的词。
        PropStageView view = director().direct("Look! The pet is big and yellow.", PET_ASSETS);

        assertEquals("pet", view.activeLemma());
        assertEquals(List.of("pet"), lemmas(view));
    }

    @Test
    void mentionedButNotPublished_isIgnored() {
        List<PropAssetView> onlyDog = List.of(new PropAssetView("dog", "pets", "/api/cet/props/dog"));

        PropStageView view = director().direct("Look! A cat and a dog.", onlyDog);

        assertEquals(List.of("dog"), lemmas(view));
    }

    @Test
    void declaredLemma_winsOverEarlierGenericMention() {
        // 开场句里「动物 / animals」先命中泛称 pet，被问的却是 cat or dog。
        String caption = "今天我们来聊聊动物和宠物。Look! Is this a cat or a dog?";

        PropStageView view = director().direct("dog", caption, PET_ASSETS);

        assertEquals("dog", view.activeLemma());
        assertEquals(List.of("dog", "pet", "cat"), lemmas(view));
    }

    @Test
    void declaredAlias_normalizesToLemma() {
        PropStageView view = director().direct("小狗", "Look! What is this?", PET_ASSETS);

        assertEquals("dog", view.activeLemma());
        assertEquals(List.of("dog"), lemmas(view));
    }

    @Test
    void declaredButNotPublished_fallsBackToMentions() {
        List<PropAssetView> onlyCat = List.of(new PropAssetView("cat", "pets", "/api/cet/props/cat"));

        PropStageView view = director().direct("dog", "Look at the cat!", onlyCat);

        assertEquals("cat", view.activeLemma());
    }

    @Test
    void declaredNothingAndNoMention_collapsesToPersonaFocus() {
        assertFalse(director().direct(null, "Nice job!", PET_ASSETS).isPropFocus());
        assertFalse(director().direct("  ", "Nice job!", PET_ASSETS).isPropFocus());
    }

    @Test
    void declaredOnly_showsPropWithoutAnyMention() {
        // 裸问句 Look! What is this? 原先不出图，声明后可以出图。
        PropStageView view = director().direct("cat", "Look! What is this?", PET_ASSETS);

        assertTrue(view.isPropFocus());
        assertEquals("cat", view.activeLemma());
    }

    @Test
    void declaredWithMentions_stillCapsAtThreeAssets() {
        List<PropAssetRow> library = List.of(
                row(1L, "dog", "[]", "pets"),
                row(2L, "cat", "[]", "pets"),
                row(3L, "fish", "[]", "pets"),
                row(4L, "bird", "[]", "pets"));
        List<PropAssetView> assets = List.of(
                new PropAssetView("dog", "pets", "/api/cet/props/dog"),
                new PropAssetView("cat", "pets", "/api/cet/props/cat"),
                new PropAssetView("fish", "pets", "/api/cet/props/fish"),
                new PropAssetView("bird", "pets", "/api/cet/props/bird"));

        PropStageView view = director(library)
                .direct("bird", "Look! A dog, a cat, a fish and a bird.", assets);

        assertEquals(List.of("bird", "dog", "cat"), lemmas(view));
    }

    @Test
    void capsAtThreeAssets() {
        List<PropAssetRow> library = List.of(
                row(1L, "dog", "[]", "pets"),
                row(2L, "cat", "[]", "pets"),
                row(3L, "fish", "[]", "pets"),
                row(4L, "bird", "[]", "pets"));
        List<PropAssetView> assets = List.of(
                new PropAssetView("dog", "pets", "/api/cet/props/dog"),
                new PropAssetView("cat", "pets", "/api/cet/props/cat"),
                new PropAssetView("fish", "pets", "/api/cet/props/fish"),
                new PropAssetView("bird", "pets", "/api/cet/props/bird"));

        PropStageView view = director(library)
                .direct("Look! A dog, a cat, a fish and a bird.", assets);

        assertEquals(List.of("dog", "cat", "fish"), lemmas(view));
    }

    private static List<String> lemmas(PropStageView view) {
        return view.assets().stream().map(PropAssetView::lemma).toList();
    }
}
