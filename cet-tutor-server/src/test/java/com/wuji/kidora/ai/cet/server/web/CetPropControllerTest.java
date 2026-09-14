package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import com.wuji.kidora.ai.cet.server.config.CetPropsProperties;
import com.wuji.kidora.ai.cet.server.props.PropFileLocator;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CetPropController 路径安全、MIME 与 ETag/304。
 *
 * @author liudy
 */
class CetPropControllerTest {

    @TempDir
    Path tempDir;

    private PropFileLocator locator;

    @BeforeEach
    void setUp() {
        CetPropsProperties props = new CetPropsProperties();
        props.setLocalDir(tempDir.toString());
        locator = new PropFileLocator(props);
    }

    private CetPropController controllerFor(List<PropAssetRow> rows) {
        return new CetPropController(new StubRepo(rows), locator, Schedulers.immediate());
    }

    @Test
    void resolve_rejectsTraversal() {
        assertNull(locator.resolve("../etc/passwd"));
        assertNull(locator.resolve("/abs/path.svg"));
        assertNull(locator.resolve("pets/../../secret.svg"));
    }

    @Test
    void resolve_acceptsRelativeUnderRoot() throws Exception {
        Path dog = writeAsset("pets/dog.svg", "<svg/>");

        Path resolved = locator.resolve("pets/dog.svg");

        assertNotNull(resolved);
        assertTrue(Files.isRegularFile(resolved));
        assertEquals(dog.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void mediaTypeOf_fromExtension() {
        assertEquals("image/svg+xml",
                PropFileLocator.mediaTypeOf(null, Path.of("dog.svg")).toString());
        assertEquals("image/png",
                PropFileLocator.mediaTypeOf("", Path.of("a.png")).toString());
        assertEquals("image/webp",
                PropFileLocator.mediaTypeOf("image/webp", Path.of("x.bin")).toString());
    }

    @Test
    void loadProp_returnsFileWithEtagAndCacheHeaders() throws Exception {
        writeAsset("pets/dog.svg", "<svg/>");
        CetPropController controller = controllerFor(List.of(row("dog", "pets/dog.svg", null)));

        ResponseEntity<Resource> response = controller.loadProp("dog", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getHeaders().getETag());
        assertTrue(response.getHeaders().getCacheControl().contains("max-age=86400"));
    }

    @Test
    void loadProp_matchingIfNoneMatch_returns304WithoutBody() throws Exception {
        writeAsset("pets/dog.svg", "<svg/>");
        CetPropController controller = controllerFor(
                List.of(row("dog", "pets/dog.svg", "a".repeat(64))));

        String etag = controller.loadProp("dog", null).getHeaders().getETag();
        ResponseEntity<Resource> cached = controller.loadProp("dog", etag);

        assertEquals(HttpStatus.NOT_MODIFIED, cached.getStatusCode());
        assertNull(cached.getBody());
    }

    @Test
    void loadProp_unknownOrMissingFile_is404() throws Exception {
        CetPropController empty = controllerFor(List.of());
        assertThrows(KidoraException.class, () -> empty.loadProp("dog", null));

        CetPropController danglingRow = controllerFor(List.of(row("dog", "pets/dog.svg", null)));
        assertThrows(KidoraException.class, () -> danglingRow.loadProp("dog", null));
    }

    @Test
    void loadProp_rejectsIllegalLemma() {
        CetPropController controller = controllerFor(List.of());
        assertThrows(KidoraException.class, () -> controller.loadProp("../etc", null));
        assertThrows(KidoraException.class, () -> controller.loadProp("", null));
    }

    private Path writeAsset(String relative, String content) throws Exception {
        Path file = tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static PropAssetRow row(String lemma, String storagePath, String checksum) {
        return new PropAssetRow(1L, lemma, "[]", "pets", storagePath,
                "image/svg+xml", 6L, "ACTIVE", checksum);
    }

    /** 无 JDBC 的内存桩。 */
    private static final class StubRepo extends CetPropAssetRepository {
        private final List<PropAssetRow> rows;

        private StubRepo(List<PropAssetRow> rows) {
            super(null);
            this.rows = rows;
        }

        @Override
        public Optional<PropAssetRow> findActiveByLemma(String lemma) {
            return rows.stream().filter(r -> r.lemma().equalsIgnoreCase(lemma)).findFirst();
        }
    }
}
