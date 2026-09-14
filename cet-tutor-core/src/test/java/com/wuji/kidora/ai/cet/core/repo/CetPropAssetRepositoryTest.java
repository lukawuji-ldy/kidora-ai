package com.wuji.kidora.ai.cet.core.repo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CetPropAssetRepository 空输入短路。
 *
 * @author liudy
 */
class CetPropAssetRepositoryTest {

    @Test
    void findActiveMatching_emptyTokens_returnsEmpty() {
        // JdbcTemplate unused when tokens empty — pass null is safe for this path
        var repo = new CetPropAssetRepository(null);
        assertTrue(repo.findActiveMatching(List.of()).isEmpty());
        assertTrue(repo.findActiveMatching(null).isEmpty());
        assertTrue(repo.findActiveMatching(List.of("  ", "")).isEmpty());
    }

    @Test
    void findActiveByLemma_blank_returnsEmpty() {
        var repo = new CetPropAssetRepository(null);
        assertTrue(repo.findActiveByLemma("").isEmpty());
        assertTrue(repo.findActiveByLemma(null).isEmpty());
    }
}
