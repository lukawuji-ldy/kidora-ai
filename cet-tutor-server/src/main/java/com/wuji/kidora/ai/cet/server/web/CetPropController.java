package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropAssetRow;
import com.wuji.kidora.ai.cet.core.repo.CetPropAssetRepository.PropCreditRow;
import com.wuji.kidora.ai.cet.server.props.PropFileLocator;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * CET 教具道具只读取图与素材署名（JWT；本地文件；命中历史库）。
 *
 * <p>{@code credits} 为保留字，不能作为道具 lemma 使用。</p>
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/cet/props")
public class CetPropController {

    private final CetPropAssetRepository propAssetRepository;
    private final PropFileLocator propFileLocator;
    private final Scheduler cetBlockingScheduler;

    public CetPropController(CetPropAssetRepository propAssetRepository,
                             PropFileLocator propFileLocator,
                             @Qualifier("cetBlockingScheduler") Scheduler cetBlockingScheduler) {
        this.propAssetRepository = propAssetRepository;
        this.propFileLocator = propFileLocator;
        this.cetBlockingScheduler = cetBlockingScheduler;
    }

    /**
     * 需要署名的素材清单（CC-BY 系列），供前台 /legal/credits 渲染。
     */
    @GetMapping("/credits")
    public Mono<ApiResponse<List<Map<String, String>>>> credits(Authentication authentication) {
        requireUser(authentication);
        return Mono.fromCallable(() -> ApiResponse.ok(propAssetRepository.listAttributionRequired()
                        .stream()
                        .map(CetPropController::toCreditItem)
                        .toList()))
                .subscribeOn(cetBlockingScheduler);
    }

    @GetMapping("/{lemma}")
    public Mono<ResponseEntity<Resource>> getProp(Authentication authentication,
                                                  @PathVariable String lemma,
                                                  @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                                                          required = false) String ifNoneMatch) {
        requireUser(authentication);
        return Mono.fromCallable(() -> loadProp(lemma, ifNoneMatch))
                .subscribeOn(cetBlockingScheduler);
    }

    ResponseEntity<Resource> loadProp(String lemma, String ifNoneMatch) {
        if (!StringUtils.hasText(lemma) || !lemma.matches("^[A-Za-z0-9_-]{1,64}$")) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "道具不存在");
        }
        String key = lemma.trim().toLowerCase(Locale.ROOT);
        Optional<PropAssetRow> rowOpt = propAssetRepository.findActiveByLemma(key);
        if (rowOpt.isEmpty()) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "道具不存在");
        }
        PropAssetRow row = rowOpt.get();
        Path file = propFileLocator.resolve(row.storagePath());
        if (file == null || !Files.isRegularFile(file)) {
            throw new KidoraException(ErrorCode.NOT_FOUND, "道具文件缺失");
        }
        String etag = etagOf(row, file);
        CacheControl cacheControl = CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic();
        if (etag != null && etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        MediaType mediaType = PropFileLocator.mediaTypeOf(row.contentType(), file);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(cacheControl);
        if (etag != null) {
            builder.eTag(etag);
        }
        return builder.body(new FileSystemResource(file));
    }

    /**
     * ETag 优先取入库校验和，缺失时退化为「文件长度 + 修改时间」。
     *
     * @return 带引号的 ETag；无法计算返回 null
     */
    static String etagOf(PropAssetRow row, Path file) {
        if (StringUtils.hasText(row.checksumSha256())) {
            return "\"" + row.checksumSha256().trim().substring(0,
                    Math.min(32, row.checksumSha256().trim().length())) + "\"";
        }
        try {
            return "\"" + Files.size(file) + "-" + Files.getLastModifiedTime(file).toMillis() + "\"";
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, String> toCreditItem(PropCreditRow row) {
        return Map.of(
                "lemma", nullToEmpty(row.lemma()),
                "theme", nullToEmpty(row.theme()),
                "source", nullToEmpty(row.sourceCode()),
                "license", nullToEmpty(row.licenseCode()),
                "licenseUrl", nullToEmpty(row.licenseUrl()),
                "author", nullToEmpty(row.author()),
                "sourceUrl", nullToEmpty(row.sourceUrl()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
