package com.wuji.kidora.ai.agent.server.learner;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import com.wuji.kidora.ai.memory.repo.LearnerProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家长名下学习者列表（供 Web 选孩子）。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/learners")
public class LearnerController {

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerController(LearnerProfileRepository learnerProfileRepository) {
        this.learnerProfileRepository = learnerProfileRepository;
    }

    @GetMapping
    public Mono<ApiResponse<List<Map<String, Object>>>> list(Authentication authentication) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<Map<String, Object>> items = learnerProfileRepository.listActiveByUserId(user.userId()).stream()
                    .map(p -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("learnerId", p.learnerId());
                        m.put("displayName", p.displayName());
                        m.put("ageBand", p.ageBand());
                        m.put("cefrLevel", p.cefrLevel());
                        m.put("preferredPersona", p.preferredPersona());
                        return m;
                    })
                    .toList();
            return ApiResponse.ok(items);
        });
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
