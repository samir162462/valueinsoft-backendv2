package com.example.valueinsoftbackend.ai.controller;

import com.example.valueinsoftbackend.ai.dto.AiChatRequest;
import com.example.valueinsoftbackend.ai.dto.AiChatResponse;
import com.example.valueinsoftbackend.ai.dto.AiConversationDto;
import com.example.valueinsoftbackend.ai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request,
                                               Principal principal) {
        return ResponseEntity.ok(aiChatService.chat(request, principal));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<AiConversationDto>> listConversations(Principal principal) {
        return ResponseEntity.ok(aiChatService.listConversations(principal));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<AiConversationDto> getConversation(@PathVariable UUID conversationId,
                                                             Principal principal) {
        return ResponseEntity.ok(aiChatService.getConversation(conversationId, principal));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID conversationId,
                                                   Principal principal) {
        aiChatService.deleteConversation(conversationId, principal);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
