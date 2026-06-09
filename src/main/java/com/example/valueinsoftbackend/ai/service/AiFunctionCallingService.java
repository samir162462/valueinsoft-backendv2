package com.example.valueinsoftbackend.ai.service;

import com.example.valueinsoftbackend.ai.dto.AiActionDto;
import com.example.valueinsoftbackend.ai.dto.AiSourceDto;
import com.example.valueinsoftbackend.ai.dto.AiToolCallDto;
import com.example.valueinsoftbackend.ai.dto.AiStreamChunk;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiChatOrchestratorService.OrchestratedChatResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

@Service
@Slf4j
public class AiFunctionCallingService {

    private final AiModelClient aiModelClient;
    private final AiToolRegistry toolRegistry;
    private final AiSystemPromptBuilder systemPromptBuilder;
    private final AiProperties aiProperties;
    private final Gson gson = new Gson();

    public AiFunctionCallingService(AiModelClient aiModelClient,
                                    AiToolRegistry toolRegistry,
                                    AiSystemPromptBuilder systemPromptBuilder,
                                    AiProperties aiProperties) {
        this.aiModelClient = aiModelClient;
        this.toolRegistry = toolRegistry;
        this.systemPromptBuilder = systemPromptBuilder;
        this.aiProperties = aiProperties;
    }

    public OrchestratedChatResult execute(String userMessage,
                                          Long branchId,
                                          AiSecurityContext securityContext,
                                          UUID conversationId,
                                          String conversationContext,
                                          String provider) {
        log.debug("Starting Gemini function calling execution. User: {}, Branch: {}", securityContext.username(), branchId);

        // 1. Build context-rich system prompt
        String systemPrompt = systemPromptBuilder.buildPrompt(securityContext, branchId);

        // 2. Track tool executions and frontend navigation actions
        List<AiToolCallDto> executedTools = new ArrayList<>();
        List<AiActionDto> actions = new ArrayList<>();

        // 3. Build tool list with interceptor callback
        List<ToolCallback> functions = toolRegistry.getTools(securityContext, conversationId, (toolName, inputJson) -> {
            log.info("Gemini invoked tool: {} with parameters: {}", toolName, inputJson);
            executedTools.add(new AiToolCallDto(toolName, "SUCCESS", inputJson));

            if ("navigateToScreen".equals(toolName)) {
                try {
                    JsonObject args = gson.fromJson(inputJson, JsonObject.class);
                    String screenName = args.get("screenName").getAsString().toUpperCase();
                    String viewId = resolveViewId(screenName);
                    
                    Map<String, Object> params = new LinkedHashMap<>();
                    params.put("viewId", viewId);
                    params.put("autoRun", true);
                    if (branchId != null) {
                        params.put("branchId", branchId);
                    }

                    actions.add(new AiActionDto(
                            "Open " + screenName,
                            "NAVIGATE",
                            "/app/view",
                            params
                    ));
                } catch (Exception e) {
                    log.error("Failed to parse navigation parameters from Gemini", e);
                }
            }
        });

        // 4. Call ChatModel
        AiModelRequest modelRequest = new AiModelRequest(
                systemPrompt,
                userMessage,
                "BUSINESS",
                "",
                conversationContext,
                provider
        );

        AiModelResponse modelResponse = aiModelClient.generateWithFunctions(modelRequest, functions);

        // 5. Build dynamic follow-up suggestions based on tools used
        List<String> suggestions = generateSuggestions(executedTools);

        return new OrchestratedChatResult(
                modelResponse.answer(),
                suggestions,
                actions,
                List.of(), // Sources
                executedTools,
                modelResponse.providerName(),
                modelResponse.providerCode()
        );
    }

    public Flux<AiStreamChunk> executeStream(String userMessage,
                                             Long branchId,
                                             AiSecurityContext securityContext,
                                             UUID conversationId,
                                             String conversationContext,
                                             String provider) {
        log.debug("Starting Gemini streaming function calling execution. User: {}, Branch: {}", securityContext.username(), branchId);

        String systemPrompt = systemPromptBuilder.buildPrompt(securityContext, branchId);
        List<AiToolCallDto> executedTools = Collections.synchronizedList(new ArrayList<>());
        List<AiActionDto> actions = Collections.synchronizedList(new ArrayList<>());

        AiModelRequest modelRequest = new AiModelRequest(
                systemPrompt,
                userMessage,
                "BUSINESS",
                "",
                conversationContext,
                provider
        );

        return Flux.create(sink -> {
            sink.next(new AiStreamChunk("thinking", "Thinking...", null));

            List<ToolCallback> functions = toolRegistry.getTools(securityContext, conversationId, (toolName, inputJson) -> {
                log.info("Gemini invoked tool in stream: {} with parameters: {}", toolName, inputJson);
                AiToolCallDto toolCall = new AiToolCallDto(toolName, "SUCCESS", inputJson);
                executedTools.add(toolCall);

                // Emit tool_call chunk to notify the client
                sink.next(new AiStreamChunk("tool_call", "Executing tool: " + toolName, toolCall));

                if ("navigateToScreen".equals(toolName)) {
                    try {
                        JsonObject args = gson.fromJson(inputJson, JsonObject.class);
                        String screenName = args.get("screenName").getAsString().toUpperCase();
                        String viewId = resolveViewId(screenName);
                        
                        Map<String, Object> params = new LinkedHashMap<>();
                        params.put("viewId", viewId);
                        params.put("autoRun", true);
                        if (branchId != null) {
                            params.put("branchId", branchId);
                        }

                        actions.add(new AiActionDto(
                                "Open " + screenName,
                                "NAVIGATE",
                                "/app/view",
                                params
                        ));
                    } catch (Exception e) {
                        log.error("Failed to parse navigation parameters from Gemini in stream", e);
                    }
                }
            });

            if (!isGeminiSelected(modelRequest)) {
                try {
                    AiModelResponse modelResponse = aiModelClient.generateWithFunctions(modelRequest, functions);
                    sink.next(new AiStreamChunk("provider", modelResponse.providerCode(), providerData(modelResponse)));
                    if (modelResponse.answer() != null && !modelResponse.answer().isBlank()) {
                        sink.next(new AiStreamChunk("delta", modelResponse.answer(), null));
                    }
                    List<String> suggestions = generateSuggestions(executedTools);
                    if (!suggestions.isEmpty()) {
                        sink.next(new AiStreamChunk("suggestions", null, suggestions));
                    }
                    sink.next(new AiStreamChunk("done", "", providerData(modelResponse)));
                } catch (RuntimeException exception) {
                    log.error("Error in non-streaming AI generation for stream endpoint type={} detail={}",
                            exception.getClass().getSimpleName(),
                            safeStreamErrorDetail(exception));
                    sink.next(new AiStreamChunk("error", "AI streaming is temporarily unavailable. Try again shortly.", null));
                } finally {
                    sink.complete();
                }
                return;
            }

            sink.next(new AiStreamChunk("provider", "GEM", Map.of("providerCode", "GEM", "providerName", "gemini")));
            aiModelClient.streamWithFunctions(modelRequest, functions)
                    .subscribe(
                            chatResponse -> {
                                if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                                    String text = chatResponse.getResult().getOutput().getText();
                                    if (text != null && !text.isEmpty()) {
                                        sink.next(new AiStreamChunk("delta", text, null));
                                    }
                                }
                            },
                            error -> {
                                log.error("Error in AI stream generation type={} detail={}",
                                        error.getClass().getSimpleName(),
                                        safeStreamErrorDetail(error));
                                
                                String fallbackProvider = aiProperties.getFallbackProvider();
                                String defaultProvider = aiProperties.getProvider();
                                String targetProvider = !"gemini".equalsIgnoreCase(fallbackProvider) && !"google".equalsIgnoreCase(fallbackProvider) && !"google-genai".equalsIgnoreCase(fallbackProvider) && !"genai".equalsIgnoreCase(fallbackProvider)
                                        ? fallbackProvider
                                        : (!"gemini".equalsIgnoreCase(defaultProvider) && !"google".equalsIgnoreCase(defaultProvider) && !"google-genai".equalsIgnoreCase(defaultProvider) && !"genai".equalsIgnoreCase(defaultProvider) ? defaultProvider : null);

                                if (targetProvider != null) {
                                    log.info("Attempting stream failure fallback to non-streaming provider={}", targetProvider);
                                    try {
                                        AiModelRequest fallbackRequest = new AiModelRequest(
                                                modelRequest.systemPrompt(),
                                                modelRequest.userMessage(),
                                                modelRequest.mode(),
                                                modelRequest.knowledgeContext(),
                                                modelRequest.conversationContext(),
                                                targetProvider
                                        );
                                        AiModelResponse modelResponse = aiModelClient.generate(fallbackRequest);
                                        sink.next(new AiStreamChunk("provider", modelResponse.providerCode(), Map.of(
                                                "providerCode", modelResponse.providerCode() == null ? "" : modelResponse.providerCode(),
                                                "providerName", modelResponse.providerName() == null ? "" : modelResponse.providerName(),
                                                "model", modelResponse.modelName() == null ? "" : modelResponse.modelName()
                                        )));
                                        if (modelResponse.answer() != null && !modelResponse.answer().isBlank()) {
                                            sink.next(new AiStreamChunk("delta", modelResponse.answer(), null));
                                        }
                                        List<String> suggestions = generateSuggestions(executedTools);
                                        if (!suggestions.isEmpty()) {
                                            sink.next(new AiStreamChunk("suggestions", null, suggestions));
                                        }
                                        sink.next(new AiStreamChunk("done", "", Map.of(
                                                "providerCode", modelResponse.providerCode() == null ? "" : modelResponse.providerCode(),
                                                "providerName", modelResponse.providerName() == null ? "" : modelResponse.providerName()
                                        )));
                                    } catch (Exception ex) {
                                        log.error("Stream fallback also failed", ex);
                                        sink.next(new AiStreamChunk("error", "AI streaming is temporarily unavailable. Try again shortly.", null));
                                    }
                                } else {
                                    sink.next(new AiStreamChunk("error", "AI streaming is temporarily unavailable. Try again shortly.", null));
                                }
                                sink.complete();
                            },
                            () -> {
                                if (!actions.isEmpty()) {
                                    sink.next(new AiStreamChunk("actions", null, actions));
                                }
                                List<String> suggestions = generateSuggestions(executedTools);
                                if (!suggestions.isEmpty()) {
                                    sink.next(new AiStreamChunk("suggestions", null, suggestions));
                                }
                                sink.next(new AiStreamChunk("done", "", Map.of("providerCode", "GEM", "providerName", "gemini")));
                                sink.complete();
                            }
                    );
        });
    }

    private String resolveViewId(String screenName) {
        return switch (screenName) {
            case "POS", "POINT OF SALE" -> "PointSale";
            case "SALES_REPORT" -> "SalesScreen";
            case "INVENTORY" -> "viewInventory";
            case "SUPPLIERS" -> "Suppliers";
            case "CUSTOMERS" -> "viewClient";
            default -> "Dashboard";
        };
    }

    private String safeStreamErrorDetail(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "n/a";
        }
        return message.length() > 180 ? message.substring(0, 180) + "..." : message;
    }

    private boolean isGeminiSelected(AiModelRequest request) {
        String selectedProvider = request != null && request.provider() != null && !request.provider().isBlank()
                ? request.provider()
                : aiProperties.getProvider();
        String normalizedProvider = selectedProvider == null ? "" : selectedProvider.trim().toLowerCase(Locale.ROOT);
        return normalizedProvider.isBlank()
                || normalizedProvider.equals("gemini")
                || normalizedProvider.equals("google")
                || normalizedProvider.equals("google-genai")
                || normalizedProvider.equals("genai");
    }

    private Map<String, String> providerData(AiModelResponse response) {
        return Map.of(
                "providerCode", response.providerCode() == null ? "" : response.providerCode(),
                "providerName", response.providerName() == null ? "" : response.providerName(),
                "model", response.modelName() == null ? "" : response.modelName()
        );
    }

    private List<String> generateSuggestions(List<AiToolCallDto> tools) {
        Set<String> suggestions = new LinkedHashSet<>();
        boolean hasSales = false;
        boolean hasInventory = false;
        boolean hasSupplier = false;
        boolean hasCustomer = false;
        boolean hasShift = false;

        for (AiToolCallDto tool : tools) {
            String name = tool.toolName().toLowerCase();
            if (name.contains("sales")) {
                hasSales = true;
            } else if (name.contains("product") || name.contains("stock")) {
                hasInventory = true;
            } else if (name.contains("supplier")) {
                hasSupplier = true;
            } else if (name.contains("customer")) {
                hasCustomer = true;
            } else if (name.contains("shift")) {
                hasShift = true;
            }
        }

        if (hasSales) {
            suggestions.add("What are today's sales?");
            suggestions.add("Top selling products this week");
            suggestions.add("Sales by cashier today");
        }
        if (hasInventory) {
            suggestions.add("Show low stock products");
            suggestions.add("Search product by name");
            suggestions.add("Check product stock by barcode");
        }
        if (hasShift) {
            suggestions.add("Current shift summary");
            suggestions.add("Open shift status");
            suggestions.add("Payment breakdown today");
        }
        if (hasSupplier) {
            suggestions.add("Show supplier balance");
            suggestions.add("Pending supplier invoices");
            suggestions.add("Top suppliers by payable");
        }
        if (hasCustomer) {
            suggestions.add("Search customer");
            suggestions.add("Customer balance by ID");
            suggestions.add("Customer last orders by ID");
        }

        // Default suggestions
        if (suggestions.isEmpty()) {
            suggestions.add("What are today's sales?");
            suggestions.add("Show low stock products");
            suggestions.add("How do I create an invoice?");
            suggestions.add("Open POS screen");
        }

        return new ArrayList<>(suggestions);
    }
}
