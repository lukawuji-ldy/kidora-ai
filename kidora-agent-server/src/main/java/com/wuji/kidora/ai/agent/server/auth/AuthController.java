package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * AuthController：登录、注册、个人资料。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public Mono<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        return DetachedBlockingMono.fromCallable(() -> ApiResponse.ok(authService.login(request)));
    }

    /**
     * 注册家长账号 + 首个儿童；返回 JWT。
     *
     * @param request 注册请求
     * @return 含 learnerId 的登录响应
     * @author liudy
     */
    @PostMapping("/auth/register")
    public Mono<ApiResponse<LoginResponse>> register(@RequestBody RegisterRequest request) {
        return DetachedBlockingMono.fromCallable(() -> ApiResponse.ok(authService.register(request)));
    }

    /**
     * 当前登录家长资料。
     *
     * @author liudy
     */
    @GetMapping("/auth/me")
    public Mono<ApiResponse<ProfileResponse>> me(Authentication authentication) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> ApiResponse.ok(authService.getProfile(user.userId())));
    }

    /**
     * 更新展示昵称；返回新 JWT。
     *
     * @author liudy
     */
    @PatchMapping("/auth/me")
    public Mono<ApiResponse<LoginResponse>> updateMe(Authentication authentication,
                                                     @RequestBody UpdateProfileRequest request) {
        AuthUser user = requireUser(authentication);
        String nickname = request == null ? null : request.nickname();
        return DetachedBlockingMono.fromCallable(
                () -> ApiResponse.ok(authService.updateNickname(user.userId(), nickname)));
    }

    /**
     * 修改密码（须验证当前密码）。
     *
     * @author liudy
     */
    @PostMapping("/auth/password")
    public Mono<ApiResponse<Map<String, Boolean>>> changePassword(Authentication authentication,
                                                                  @RequestBody ChangePasswordRequest request) {
        AuthUser user = requireUser(authentication);
        String current = request == null ? null : request.currentPassword();
        String next = request == null ? null : request.newPassword();
        return DetachedBlockingMono.fromCallable(() -> {
            authService.changePassword(user.userId(), current, next);
            return ApiResponse.ok(Map.of("ok", true));
        });
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return authUser;
    }
}
