package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiStreamChunk;
import com.example.valueinsoftbackend.ai.service.AiChatService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiStreamController {

    private final AiChatService aiChatService;

    public AiStreamController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamChunk>> chatStream(@Valid @RequestBody AiChatRequest request,
                                                           Principal principal) {
        return Flux.defer(() -> aiChatService.chatStream(request, principal))
                .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                        .data(chunk)
                        .build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<AiStreamChunk>builder()
                        .data(toErrorChunk(error))
                        .build()));
    }

    private AiStreamChunk toErrorChunk(Throwable error) {
        if (error instanceof ApiException apiException) {
            String errorCode = apiException.getCode() == null || apiException.getCode().isBlank()
                    ? "AI_REQUEST_REJECTED"
                    : apiException.getCode();
            log.warn("AI stream request rejected code={} status={}",
                    errorCode, apiException.getStatus().value());
            return new AiStreamChunk(
                    "error",
                    apiException.getMessage(),
                    Map.of(
                            "code", errorCode,
                            "retryable", apiException.getStatus().is5xxServerError()
                                    || apiException.getStatus().value() == 429
                    )
            );
        }
        log.error("Unexpected AI streaming failure", error);
        return new AiStreamChunk(
                "error",
                "AI streaming is temporarily unavailable. Try again shortly.",
                Map.of("code", "AI_STREAM_UNAVAILABLE", "retryable", true)
        );
    }
}
