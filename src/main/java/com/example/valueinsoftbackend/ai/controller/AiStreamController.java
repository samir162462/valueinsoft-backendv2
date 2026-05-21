package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiStreamChunk;
import com.example.valueinsoftbackend.ai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.security.Principal;

@RestController
@RequestMapping("/api/ai")
public class AiStreamController {

    private final AiChatService aiChatService;

    public AiStreamController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamChunk>> chatStream(@Valid @RequestBody AiChatRequest request,
                                                           Principal principal) {
        return aiChatService.chatStream(request, principal)
                .map(chunk -> ServerSentEvent.<AiStreamChunk>builder()
                        .data(chunk)
                        .build())
                .onErrorResume(error -> Flux.just(ServerSentEvent.<AiStreamChunk>builder()
                        .data(new AiStreamChunk("error", "An error occurred: " + error.getMessage(), null))
                        .build()));
    }
}
