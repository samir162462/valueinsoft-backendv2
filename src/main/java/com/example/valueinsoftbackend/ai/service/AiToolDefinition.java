package com.example.valueinsoftbackend.ai.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * Custom implementation of Spring AI's ToolCallback to register and execute
 * tools on behalf of Gemini.
 */
public class AiToolDefinition implements ToolCallback {

    private final String name;
    private final String description;
    private final String inputTypeSchema;
    private final ToolExecutor executor;

    public AiToolDefinition(String name, String description, String inputTypeSchema, ToolExecutor executor) {
        this.name = name;
        this.description = description;
        this.inputTypeSchema = inputTypeSchema;
        this.executor = executor;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(inputTypeSchema)
                .build();
    }

    @Override
    public String call(String toolInput) {
        try {
            return executor.execute(toolInput);
        } catch (Exception e) {
            return "Error executing tool " + name + ": " + e.getMessage();
        }
    }

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(String inputJson) throws Exception;
    }
}
