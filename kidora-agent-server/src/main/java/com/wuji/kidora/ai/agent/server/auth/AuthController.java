package com.wuji.kidora.ai.agent.server.auth;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * AuthController.
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
}
