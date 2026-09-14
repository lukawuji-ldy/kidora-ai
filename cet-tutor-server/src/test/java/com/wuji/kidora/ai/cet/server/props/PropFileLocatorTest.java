package com.wuji.kidora.ai.cet.server.props;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 道具根目录解析：相对路径要同时容忍「从模块启动」与「从仓库根启动」两种工作目录。
 *
 * @author liudy
 */
class PropFileLocatorTest {

    @TempDir
    Path tempDir;

    @Test
    void absoluteDir_isUsedAsIs() {
        Path configured = tempDir.resolve("props");

        assertEquals(configured.normalize(),
                PropFileLocator.resolveRoot(configured.toString(), tempDir.resolve("elsewhere")));
    }

    @Test
    void relativeDirUnderWorkingDir_wins() throws Exception {
        Path expected = Files.createDirectories(tempDir.resolve("data/cet-props"));

        assertEquals(expected.normalize(),
                PropFileLocator.resolveRoot("./data/cet-props", tempDir));
    }

    @Test
    void relativeDirMissing_fallsBackToModuleSubdirectory() throws Exception {
        // 从仓库根启动时的实际布局：cet-tutor-server/data/cet-props
        Path expected = Files.createDirectories(
                tempDir.resolve("cet-tutor-server/data/cet-props"));

        assertEquals(expected.normalize(),
                PropFileLocator.resolveRoot("./data/cet-props", tempDir));
    }

    @Test
    void workingDirWins_whenBothExist() throws Exception {
        Path expected = Files.createDirectories(tempDir.resolve("data/cet-props"));
        Files.createDirectories(tempDir.resolve("cet-tutor-server/data/cet-props"));

        assertEquals(expected.normalize(),
                PropFileLocator.resolveRoot("./data/cet-props", tempDir));
    }

    @Test
    void neitherExists_keepsConfiguredPathForDiagnostics() {
        assertEquals(tempDir.resolve("data/cet-props").normalize(),
                PropFileLocator.resolveRoot("./data/cet-props", tempDir));
    }
}
