package com.wuji.kidora.ai.cet.server.props;

import com.wuji.kidora.ai.cet.server.config.CetPropsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 把 {@code cet_prop_asset.storage_path} 解析到本地道具根目录，并禁止路径穿越。
 *
 * @author liudy
 */
@Component
public class PropFileLocator {

    private static final Logger log = LoggerFactory.getLogger(PropFileLocator.class);

    /** 相对目录探测时的备选前缀：进程从仓库根启动时，道具目录在本模块下。 */
    private static final String MODULE_DIR = "cet-tutor-server";

    private final Path root;

    public PropFileLocator(CetPropsProperties propsProperties) {
        this.root = resolveRoot(propsProperties.getLocalDir(), Paths.get("").toAbsolutePath());
    }

    /** 道具文件根目录（绝对化）。 */
    public Path root() {
        return root;
    }

    /**
     * 解析道具根目录。配置值为相对路径时容忍两种工作目录：进程从本模块启动，
     * 或从仓库根启动（IDE 默认工作目录与 {@code mvn} 的差异是本地 404 的常见来源）。
     *
     * @param localDir   配置的道具根目录
     * @param workingDir 进程工作目录
     * @return 绝对化后的根目录；两处都不存在时返回按工作目录解析的结果，让日志与报错指向实际配的路径
     */
    static Path resolveRoot(String localDir, Path workingDir) {
        Path configured = Paths.get(localDir);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path primary = workingDir.resolve(configured).normalize();
        if (Files.isDirectory(primary)) {
            return primary;
        }
        Path underModule = workingDir.resolve(MODULE_DIR).resolve(configured).normalize();
        if (Files.isDirectory(underModule)) {
            log.warn("CET props dir {} not found under working directory; falling back to {}. "
                            + "设置 KIDORA_CET_PROPS_DIR 或把工作目录指向 {} 可避免本次探测。",
                    primary, underModule, MODULE_DIR);
            return underModule;
        }
        return primary;
    }

    /**
     * 解析相对路径。
     *
     * @param storagePath 库中记录的相对路径
     * @return 根目录下的绝对路径；非法（绝对路径 / 穿越 / 空）返回 null
     */
    public Path resolve(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return null;
        }
        String relative = storagePath.trim().replace('\\', '/');
        if (relative.startsWith("/") || relative.contains("..")) {
            return null;
        }
        Path root = root();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            return null;
        }
        return resolved;
    }

    /**
     * 优先用库中 content_type，其次按扩展名推断。
     *
     * @param contentType 库中 MIME
     * @param file        文件路径
     * @return MediaType
     */
    public static MediaType mediaTypeOf(String contentType, Path file) {
        if (StringUtils.hasText(contentType)) {
            try {
                return MediaType.parseMediaType(contentType.trim());
            } catch (Exception ignored) {
                // 库中 MIME 不合法时回退扩展名推断
            }
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (name.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (name.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
