package com.wuji.kidora.ai.cet.server.web;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.cet.core.props.PropAssetResolver;
import com.wuji.kidora.ai.cet.core.repo.CetLessonSessionRepository;
import com.wuji.kidora.ai.cet.core.repo.CetTutorTurnRepository;
import com.wuji.kidora.ai.cet.core.service.CetLessonService;
import com.wuji.kidora.ai.cet.core.speech.CetStreamEvent;
import com.wuji.kidora.ai.cet.core.speech.TurnInput;
import com.wuji.kidora.ai.common.api.ApiResponse;
import com.wuji.kidora.ai.common.auth.AuthUser;
import com.wuji.kidora.ai.common.exception.ErrorCode;
import com.wuji.kidora.ai.common.exception.KidoraException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CetSessionController.
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/cet")
public class CetSessionController {

    private static final Logger log = LoggerFactory.getLogger(CetSessionController.class);

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
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionId", result.sessionId());
            body.put("status", result.status());
            body.put("planSummary", result.planSummary());
            body.put("childGoals", result.childGoals() == null ? List.of() : result.childGoals());
            body.put("vocabHints", result.vocabHints() == null ? List.of() : result.vocabHints());
            body.put("propAssets", toPropAssetMaps(result.propAssets()));
            return ApiResponse.ok(body);
        });
    }

    @GetMapping("/sessions")
    public Mono<ApiResponse<List<Map<String, Object>>>> list(Authentication authentication,
                                                             @RequestParam String learnerId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<Map<String, Object>> items = cetLessonService.listSessions(user.userId(), learnerId).stream()
                    .map(CetSessionController::toSessionListMap)
                    .toList();
            return ApiResponse.ok(items);
        });
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Map<String, Object>>> get(Authentication authentication,
                                                      @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.SessionDetail d = cetLessonService.getSession(user.userId(), sessionId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionId", d.sessionId());
            body.put("learnerId", d.learnerId());
            body.put("topic", nullToEmpty(d.topic()));
            body.put("personaId", nullToEmpty(d.personaId()));
            body.put("cefrLevel", nullToEmpty(d.cefrLevel()));
            body.put("status", d.status());
            body.put("planSummary", nullToEmpty(d.planSummary()));
            body.put("hasReport", d.hasReport());
            body.put("childGoals", d.childGoals() == null ? List.of() : d.childGoals());
            body.put("vocabHints", d.vocabHints() == null ? List.of() : d.vocabHints());
            body.put("propAssets", toPropAssetMaps(d.propAssets()));
            body.put("wrapUpPhase", d.wrapUpPhase());
            return ApiResponse.ok(body);
        });
    }

    @GetMapping("/sessions/{sessionId}/turns")
    public Mono<ApiResponse<List<Map<String, Object>>>> turns(Authentication authentication,
                                                              @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<Map<String, Object>> items = cetLessonService.listTurns(user.userId(), sessionId).stream()
                    .map(CetSessionController::toTurnMap)
                    .toList();
            return ApiResponse.ok(items);
        });
    }

    @PostMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(Authentication authentication,
                                                @PathVariable String sessionId,
                                                @RequestBody StreamTurnRequest request) {
        AuthUser user = requireUser(authentication);
        int modeFlags = (Boolean.TRUE.equals(request.opening()) ? 1 : 0)
                + (Boolean.TRUE.equals(request.wrapUp()) ? 1 : 0)
                + (Boolean.TRUE.equals(request.wrapUpTimeout()) ? 1 : 0);
        if (modeFlags > 1) {
            return Flux.just(
                    ServerSentEvent.<String>builder()
                            .event("error")
                            .data(ErrorCode.CET_INVALID_STATE.getCode() + ":opening/wrapUp/wrapUpTimeout 互斥")
                            .build(),
                    ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
        }
        Flux<CetStreamEvent> events;
        if (Boolean.TRUE.equals(request.opening())) {
            events = Flux.defer(() -> cetLessonService.streamOpening(user.userId(), sessionId));
        } else if (Boolean.TRUE.equals(request.wrapUp())) {
            events = Flux.defer(() -> cetLessonService.streamWrapUpTutor(user.userId(), sessionId));
        } else if (Boolean.TRUE.equals(request.wrapUpTimeout())) {
            events = Flux.defer(() -> cetLessonService.streamWrapUpTimeout(user.userId(), sessionId));
        } else {
            events = Flux.defer(() -> cetLessonService.streamTurn(user.userId(), sessionId,
                    new TurnInput(request.text(), request.audioBase64(),
                            request.locale(), request.referenceText())));
        }
        return events
                .subscribeOn(cetBlockingScheduler)
                .map(CetSessionController::toSse)
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(KidoraException.class, ex -> {
                    log.warn("CET stream KidoraException {} : {}", ex.getErrorCode(), ex.getMessage());
                    String event = ex.getErrorCode() == ErrorCode.CET_SAFETY_BLOCKED ? "safety.block" : "error";
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event(event)
                                    .data(ex.getErrorCode().getCode() + ":" + ex.getMessage())
                                    .build(),
                            ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                    );
                })
                .onErrorResume(Throwable.class, ex -> {
                    log.error("CET stream unhandled error sessionId={}", sessionId, ex);
                    return Flux.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(ErrorCode.INTERNAL_ERROR.getCode() + ":" + ErrorCode.INTERNAL_ERROR.getMessage())
                                    .build(),
                            ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                    );
                });
    }

    static ServerSentEvent<String> toSse(CetStreamEvent event) {
        String name = switch (event.type()) {
            case DELTA -> "message.delta";
            case ASR -> "asr.transcript";
            case TTS -> "audio.tts";
            case PRONUNCIATION -> "pronunciation";
            case PLAN_UPDATED -> "plan.updated";
            case TIMING -> "turn.timing";
            case WRAPUP -> "session.wrapup";
            case SESSION_COMPLETED -> "session.completed";
            case PROP -> "turn.prop";
        };
        return ServerSentEvent.<String>builder().event(name).data(event.data()).build();
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

    @PostMapping("/sessions/{sessionId}/abort")
    public Mono<ApiResponse<Map<String, Object>>> abort(Authentication authentication,
                                                        @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.AbortResult result = cetLessonService.abortSession(user.userId(), sessionId);
            return ApiResponse.ok(Map.of(
                    "sessionId", result.sessionId(),
                    "status", result.status()
            ));
        });
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<ApiResponse<Map<String, Object>>> deleteOne(Authentication authentication,
                                                            @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            int n = cetLessonService.deleteSessions(user.userId(), List.of(sessionId));
            return ApiResponse.ok(Map.of("deletedCount", n));
        });
    }

    @PostMapping("/sessions/batch-delete")
    public Mono<ApiResponse<Map<String, Object>>> deleteBatch(Authentication authentication,
                                                              @RequestBody BatchDeleteRequest request) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<String> ids = request == null || request.sessionIds() == null
                    ? List.of()
                    : request.sessionIds();
            int n = cetLessonService.deleteSessions(user.userId(), ids);
            return ApiResponse.ok(Map.of("deletedCount", n));
        });
    }

    @GetMapping("/sessions/{sessionId}/report")
    public Mono<ApiResponse<Map<String, Object>>> report(Authentication authentication,
                                                         @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.ReportResult result = cetLessonService.getReport(user.userId(), sessionId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("childSummary", result.childSummary());
            body.put("parentSummary", nullToEmpty(result.parentSummary()));
            body.put("assessment", result.assessment());
            body.put("topic", nullToEmpty(result.topic()));
            body.put("learnerName", nullToEmpty(result.learnerName()));
            body.put("cefrLevel", nullToEmpty(result.cefrLevel()));
            body.put("completedAt", result.completedAt() == null ? "" : result.completedAt());
            return ApiResponse.ok(body);
        });
    }

    @PostMapping("/sessions/{sessionId}/turns/{turnIndex}/tts")
    public Mono<ApiResponse<Map<String, Object>>> replayTts(Authentication authentication,
                                                            @PathVariable String sessionId,
                                                            @PathVariable int turnIndex) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            CetLessonService.TtsReplayResult tts = cetLessonService.replayTutorTts(
                    user.userId(), sessionId, turnIndex);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("audioBase64", nullToEmpty(tts.audioBase64()));
            body.put("mimeType", nullToEmpty(tts.mimeType()));
            body.put("provider", nullToEmpty(tts.provider()));
            return ApiResponse.ok(body);
        });
    }

    /**
     * 客户端上报停麦→外教开播 e2e 耗时（毫秒）。
     *
     * @param authentication 用户 JWT
     * @param sessionId      会话
     * @param turnIndex      轮次
     * @param request        body
     * @return ok
     * @author liudy
     */
    @PostMapping("/sessions/{sessionId}/turns/{turnIndex}/client-timing")
    public Mono<ApiResponse<Map<String, Object>>> clientTiming(Authentication authentication,
                                                               @PathVariable String sessionId,
                                                               @PathVariable int turnIndex,
                                                               @RequestBody ClientTimingRequest request) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            int ms = request == null || request.e2eHeardMs() == null ? -1 : request.e2eHeardMs();
            cetLessonService.recordClientTiming(user.userId(), sessionId, turnIndex, ms);
            return ApiResponse.ok(Map.of("ok", true));
        });
    }

    static Map<String, Object> toSessionListMap(CetLessonSessionRepository.SessionListItem item) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", item.sessionId());
        m.put("learnerId", item.learnerId());
        m.put("topic", nullToEmpty(item.topic()));
        m.put("personaId", nullToEmpty(item.personaId()));
        m.put("cefrLevel", nullToEmpty(item.cefrLevel()));
        m.put("status", item.status());
        m.put("createTime", formatInstant(item.createTime()));
        m.put("startTime", formatInstant(item.startTime()));
        m.put("endTime", formatInstant(item.endTime()));
        m.put("hasReport", item.hasReport());
        return m;
    }

    static Map<String, Object> toTurnMap(CetTutorTurnRepository.TurnRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("turnIndex", row.turnIndex());
        m.put("stageId", nullToEmpty(row.stageId()));
        m.put("tutorText", nullToEmpty(row.tutorText()));
        m.put("childText", nullToEmpty(row.childText()));
        m.put("createTime", formatInstant(row.createTime()));
        return m;
    }

    static List<Map<String, Object>> toPropAssetMaps(List<PropAssetResolver.PropAssetView> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> out = new java.util.ArrayList<>(assets.size());
        for (PropAssetResolver.PropAssetView a : assets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lemma", nullToEmpty(a.lemma()));
            m.put("theme", nullToEmpty(a.theme()));
            m.put("url", nullToEmpty(a.url()));
            out.add(m);
        }
        return out;
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "" : instant.toString();
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

    public record OpenSessionRequest(String learnerId, String topic, String personaId) {
    }

    /**
     * 批量删除上课记录请求。
     *
     * @param sessionIds 会话业务键列表
     * @author liudy
     */
    public record BatchDeleteRequest(List<String> sessionIds) {
    }

    /**
     * 陪练轮次请求：opening=true 为开场；否则 text 与 audioBase64 二选一。
     *
     * @param text          文本
     * @param audioBase64   音频
     * @param locale        区域
     * @param referenceText 发音参考
     * @param opening       是否开场外教先说
     * @param wrapUp        是否仅外教告别第 1 句
     * @param wrapUpTimeout 孩子超时未回再见时的兜底结课
     * @author liudy
     */
    public record StreamTurnRequest(String text, String audioBase64, String locale, String referenceText,
                                    Boolean opening, Boolean wrapUp, Boolean wrapUpTimeout) {
    }

    /**
     * 客户端 e2e 耗时上报。
     *
     * @param e2eHeardMs 停麦到开播毫秒
     * @author liudy
     */
    public record ClientTimingRequest(Integer e2eHeardMs) {
    }
}
