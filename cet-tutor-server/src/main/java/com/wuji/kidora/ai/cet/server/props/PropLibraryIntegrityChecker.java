package com.wuji.kidora.ai.cet.server.props;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动时体检道具库：库里有行但磁盘缺文件是最常见的线上故障，只告警不阻断启动。
 *
 * <p>完整体检（含孤儿文件与校验和比对）见 {@code scripts/props/verify-props.mjs}。</p>
 *
 * @author liudy
 */
@Component
public class PropLibraryIntegrityChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PropLibraryIntegrityChecker.class);
    private static final int MAX_REPORTED = 20;

    private final CetPropAssetRepository propAssetRepository;
    private final PropFileLocator propFileLocator;

    public PropLibraryIntegrityChecker(CetPropAssetRepository propAssetRepository,
                                       PropFileLocator propFileLocator) {
        this.propAssetRepository = propAssetRepository;
        this.propFileLocator = propFileLocator;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            check();
        } catch (RuntimeException e) {
            log.warn("CET prop library integrity check skipped", e);
        }
    }

    private void check() {
        List<PropAssetRow> rows = propAssetRepository.listActive();
        if (rows.isEmpty()) {
            log.info("CET prop library is empty; lessons will run without prop stage");
            return;
        }
        List<String> missing = new ArrayList<>();
        for (PropAssetRow row : rows) {
            Path file = propFileLocator.resolve(row.storagePath());
            if (file == null || !Files.isRegularFile(file)) {
                missing.add(row.lemma() + " -> " + row.storagePath());
            }
        }
        if (missing.isEmpty()) {
            log.info("CET prop library OK: {} active assets under {}", rows.size(), propFileLocator.root());
            return;
        }
        log.warn("CET prop library has {} active rows without a readable file under {}; "
                        + "run scripts/props/verify-props.mjs. First {}: {}",
                missing.size(), propFileLocator.root(),
                Math.min(MAX_REPORTED, missing.size()),
                missing.subList(0, Math.min(MAX_REPORTED, missing.size())));
    }
}
