package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.cet.core.service.CetLessonService;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.Map;

/**
 * CetSessionController.
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/cet")
public class CetSessionController {

    private final CetLessonService cetLessonService;
    private final Scheduler cetBlockingScheduler;

    public CetSessionController(CetLessonService cetLessonService,
                                @Qualifier("cetBlockingScheduler") Scheduler cetBlockingScheduler) {
        this.cetLessonService = cetLessonService;
        this.cetBlockingScheduler = cetBlockingScheduler;
    }

    @PostMapping("/sessions")
    public Mono<ApiResponse<Map<String, Object>>> open(Authentication authentication,
                                                       @RequestBody OpenSessionRequest request) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.OpenResult result = cetLessonService.openSession(
                    user.userId(), request.learnerId(), request.topic(), request.personaId());
            return ApiResponse.ok(Map.of(
                    "sessionId", result.sessionId(),
                    "status", result.status(),
                    "planSummary", result.planSummary()
            ));
        });
    }

    @PostMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(Authentication authentication,
                                                @PathVariable String sessionId,
                                                @RequestBody StreamTurnRequest request) {
        AuthUser user = requireUser(authentication);
        return Flux.defer(() -> cetLessonService.streamTurn(user.userId(), sessionId, request.text()))
                .subscribeOn(cetBlockingScheduler)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message.delta")
                        .data(chunk)
                        .build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(KidoraException.class, ex -> {
                    String event = ex.getErrorCode() == ErrorCode.CET_SAFETY_BLOCKED ? "safety.block" : "error";
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event(event)
                                    .data(ex.getErrorCode().getCode() + ":" + ex.getMessage())
                                    .build(),
                            ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                    );
                });
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public Mono<ApiResponse<Map<String, Object>>> complete(Authentication authentication,
                                                           @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.CompleteResult result = cetLessonService.complete(user.userId(), sessionId);
            return ApiResponse.ok(Map.of(
                    "sessionId", result.sessionId(),
                    "status", result.status(),
                    "childSummary", result.childSummary(),
                    "assessment", result.assessmentJson()
            ));
        });
    }

    @GetMapping("/sessions/{sessionId}/report")
    public Mono<ApiResponse<Map<String, Object>>> report(Authentication authentication,
                                                         @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.ReportResult result = cetLessonService.getReport(user.userId(), sessionId);
            return ApiResponse.ok(Map.of(
                    "childSummary", result.childSummary(),
                    "assessment", result.assessment()
            ));
        });
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public record OpenSessionRequest(String learnerId, String topic, String personaId) {
    }

    public record StreamTurnRequest(String text) {
    }
}
