package com.wuji.kidora.ai.agent.server.chat;

import com.wuji.kidora.ai.agent.DetachedBlockingMono;
import com.wuji.kidora.ai.agent.chat.ChatFacade;
import com.wuji.kidora.ai.agent.chat.ChatMessageRepository;
import com.wuji.kidora.ai.agent.chat.ChatSessionRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 Chat API。
 *
 * @author liudy
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatFacade chatFacade;
    private final Scheduler chatBlockingScheduler;

    public ChatController(ChatFacade chatFacade,
                          @Qualifier("chatBlockingScheduler") Scheduler chatBlockingScheduler) {
        this.chatFacade = chatFacade;
        this.chatBlockingScheduler = chatBlockingScheduler;
    }

    @PostMapping("/sessions")
    public Mono<ApiResponse<Map<String, Object>>> create(Authentication authentication,
                                                         @RequestBody(required = false) CreateSessionRequest request) {
        AuthUser user = requireUser(authentication);
        String title = request == null ? null : request.title();
        return DetachedBlockingMono.fromCallable(() -> {
            String sessionId = chatFacade.createSession(user.userId(), title);
            return ApiResponse.ok(Map.of("sessionId", sessionId));
        });
    }

    @GetMapping("/sessions")
    public Mono<ApiResponse<List<Map<String, Object>>>> list(Authentication authentication) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<Map<String, Object>> items = chatFacade.listSessions(user.userId()).stream()
                    .map(ChatController::toSessionMap)
                    .toList();
            return ApiResponse.ok(items);
        });
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Mono<ApiResponse<List<Map<String, Object>>>> messages(Authentication authentication,
                                                                 @PathVariable String sessionId) {
        AuthUser user = requireUser(authentication);
        return DetachedBlockingMono.fromCallable(() -> {
            List<Map<String, Object>> items = chatFacade.listMessages(user.userId(), sessionId).stream()
                    .map(ChatController::toMessageMap)
                    .toList();
            return ApiResponse.ok(items);
        });
    }

    @PostMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(Authentication authentication,
                                                @PathVariable String sessionId,
                                                @RequestBody StreamRequest request) {
        AuthUser user = requireUser(authentication);
        return Flux.defer(() -> chatFacade.streamReply(user.userId(), sessionId, request.text()))
                .subscribeOn(chatBlockingScheduler)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message.delta")
                        .data(chunk)
                        .build())
                .concatWith(Mono.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(KidoraException.class, ex -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(ex.getErrorCode().getCode() + ":" + ex.getMessage())
                                .build(),
                        ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                ))
                .onErrorResume(Throwable.class, ex -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(ErrorCode.INTERNAL_ERROR.getCode() + ":" + ex.getMessage())
                                .build(),
                        ServerSentEvent.<String>builder().event("done").data("[DONE]").build()
                ));
    }

    private static Map<String, Object> toSessionMap(ChatSessionRepository.SessionRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", row.sessionId());
        m.put("title", row.title());
        m.put("messageCount", row.messageCount());
        m.put("lastActiveTime", row.lastActiveTime().toString());
        return m;
    }

    private static Map<String, Object> toMessageMap(ChatMessageRepository.MessageRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("messageId", row.messageId());
        m.put("role", row.role());
        m.put("content", row.content());
        m.put("status", row.status());
        m.put("createTime", row.createTime().toString());
        return m;
    }

    private static AuthUser requireUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)) {
            throw new KidoraException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public record CreateSessionRequest(String title) {
    }

    public record StreamRequest(String text) {
    }
}
