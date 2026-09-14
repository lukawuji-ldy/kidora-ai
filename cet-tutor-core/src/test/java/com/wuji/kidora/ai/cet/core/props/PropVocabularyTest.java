package com.wuji.kidora.ai.cet.core.props;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.wuji.kidora.ai.cet.core.props.StubPropAssetRepository.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 词表以 aliases_json 为唯一权威，复数由通用规则还原。
 *
 * @author liudy
 */
class PropVocabularyTest {

    private static PropVocabulary vocabulary() {
        return new PropVocabulary(new StubPropAssetRepository(List.of(
                row(1L, "dog", "[\"puppy\",\"狗\"]", "pets"),
                row(2L, "pet", "[\"宠物\"]", "pets"),
                row(3L, "baby", "[]", "family"),
                row(4L, "box", "[]", "default"))), 600L);
    }

    @Test
    void normalize_lemmaAndDatabaseAlias() {
        PropVocabulary vocab = vocabulary();
        assertEquals("dog", vocab.normalize("dog"));
        assertEquals("dog", vocab.normalize("Puppy"));
        assertEquals("dog", vocab.normalize("狗"));
        assertEquals("pet", vocab.normalize("宠物"));
    }

    @Test
    void normalize_stripsPluralWithoutHardcodedTable() {
        PropVocabulary vocab = vocabulary();
        assertEquals("pet", vocab.normalize("pets"));
        assertEquals("dog", vocab.normalize("dogs"));
        assertEquals("box", vocab.normalize("boxes"));
        assertEquals("baby", vocab.normalize("babies"));
    }

    @Test
    void normalize_unknownWord_returnsNull() {
        PropVocabulary vocab = vocabulary();
        assertNull(vocab.normalize("zebra"));
        assertNull(vocab.normalize(""));
        assertNull(vocab.normalize(null));
    }

    @Test
    void surfaceForms_containLemmaAndAliases() {
        PropVocabulary vocab = vocabulary();
        assertTrue(vocab.surfaceFormsOf("dog").containsAll(List.of("dog", "puppy", "狗")));
        assertEquals(List.of("zebra"), List.copyOf(vocab.surfaceFormsOf("zebra")));
    }

    @Test
    void reloadFailure_keepsPreviousSnapshot() {
        FlakyRepo repo = new FlakyRepo();
        PropVocabulary vocab = new PropVocabulary(repo, 600L);
        assertEquals("dog", vocab.normalize("puppy"));

        repo.failing = true;
        vocab.invalidate();

        assertEquals("dog", vocab.normalize("puppy"), "加载失败应沿用旧快照而不是全线失效");
    }

    /** 第二次加载抛错的仓储桩。 */
    private static final class FlakyRepo extends CetPropAssetRepository {
        private boolean failing;

        private FlakyRepo() {
            super(null);
        }

        @Override
        public List<PropAssetRow> listActive() {
            if (failing) {
                throw new IllegalStateException("db down");
            }
            return List.of(row(1L, "dog", "[\"puppy\"]", "pets"));
        }

        @Override
        public List<PropAssetRow> findActiveMatching(Collection<String> tokens) {
            return List.of();
        }
    }
}
