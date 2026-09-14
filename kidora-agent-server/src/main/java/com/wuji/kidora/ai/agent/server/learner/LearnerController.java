package com.wuji.kidora.ai.agent.server.learner;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 家长名下学习者列表与档案维护（供 Web 选孩子 / 个人中心）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/learners")
public class LearnerController {

    private final LearnerService learnerService;

    public LearnerController(LearnerService learnerService) {
        this.learnerService = learnerService;
    }

    @GetMapping
    public Mono<ApiResponse<List<Map<String, Object>>>> list(Authentication authentication) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> ApiResponse.ok(learnerService.listActive(user.userId())));
    }

    /**
     * 添加儿童档案。
     *
     * @author liudy
     */
    @PostMapping
    public Mono<ApiResponse<Map<String, Object>>> create(Authentication authentication,
                                                         @RequestBody CreateLearnerRequest request) {
        AuthUser user = requireUser(authentication);
        String name = request == null ? null : request.displayName();
        String level = request == null ? null : request.englishLevel();
        return DetachedBlockingMono.fromCallable(
                () -> ApiResponse.ok(learnerService.create(user.userId(), name, level)));
    }

    /**
     * 编辑儿童昵称 / 英语水平。
     *
     * @author liudy
     */
    @PatchMapping("/{learnerId}")
    public Mono<ApiResponse<Map<String, Object>>> update(Authentication authentication,
                                                         @PathVariable String learnerId,
                                                         @RequestBody UpdateLearnerRequest request) {
        AuthUser user = requireUser(authentication);
        String name = request == null ? null : request.displayName();
        String level = request == null ? null : request.englishLevel();
        return DetachedBlockingMono.fromCallable(
                () -> ApiResponse.ok(learnerService.update(user.userId(), learnerId, name, level)));
    }

    /**
     * 软删除儿童档案。
     *
     * @author liudy
     */
    @DeleteMapping("/{learnerId}")
    public Mono<ApiResponse<Map<String, Boolean>>> delete(Authentication authentication,
                                                          @PathVariable String learnerId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            learnerService.softDelete(user.userId(), learnerId);
            return ApiResponse.ok(Map.of("ok", true));
        });
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
