package com.example.valueinsoftbackend.ai.sql;

import com.example.valueinsoftbackend.ai.audit.AiToolAuditService;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.example.valueinsoftbackend.ai.service.AiSecurityContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class AiSqlAgentService {

    private final AiModelClient modelClient;
    private final AiSqlSchemaCatalog schemaCatalog;
    private final AiSqlValidator sqlValidator;
    private final AiSqlExecutor sqlExecutor;
    private final AiToolAuditService auditService;
    private final Gson gson;

    public AiSqlAgentService(AiModelClient modelClient,
                             AiSqlSchemaCatalog schemaCatalog,
                             AiSqlValidator sqlValidator,
                             AiSqlExecutor sqlExecutor,
                             AiToolAuditService auditService,
                             Gson gson) {
        this.modelClient = modelClient;
        this.schemaCatalog = schemaCatalog;
        this.sqlValidator = sqlValidator;
        this.sqlExecutor = sqlExecutor;
        this.auditService = auditService;
        this.gson = gson;
    }

    public AiSqlAnswer answer(AiSecurityContext context,
                              UUID conversationId,
                              Long branchId,
                              String userQuestion,
                              String conversationContext) {
        long startedAt = System.nanoTime();
        String generatedSql = null;
        try {
            AiSqlPlan plan = planSql(context, branchId, userQuestion, conversationContext);
            generatedSql = plan.sql();
            String validatedSql = sqlValidator.validate(generatedSql, context.companyId(), branchId);
            log.info(
                    "AI SQL SELECT approved conversationId={} companyId={} branchId={} userId={} sql={}",
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    oneLine(validatedSql)
            );
            List<Map<String, Object>> rows = sqlExecutor.execute(validatedSql, context.companyId(), branchId);
            log.info(
                    "AI SQL SELECT executed conversationId={} companyId={} branchId={} rowCount={} durationMs={}",
                    conversationId,
                    context.companyId(),
                    branchId,
                    rows.size(),
                    elapsedMs(startedAt)
            );
            String answer = summarize(userQuestion, conversationContext, validatedSql, rows);
            auditService.logToolCall(
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    "aiSqlSelect",
                    Map.of("question", userQuestion, "sql", validatedSql),
                    "Executed read-only SELECT and returned " + rows.size() + " row(s)",
                    true,
                    null,
                    elapsedMs(startedAt)
            );
            return new AiSqlAnswer(answer, validatedSql, rows.size());
        } catch (RuntimeException exception) {
            log.warn(
                    "AI SQL SELECT rejected conversationId={} companyId={} branchId={} userId={} sql={} reason={}",
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    generatedSql == null ? "" : oneLine(generatedSql),
                    exception.getMessage()
            );
            auditService.logToolCall(
                    conversationId,
                    context.companyId(),
                    branchId,
                    context.userId(),
                    "aiSqlSelect",
                    Map.of("question", userQuestion, "sql", generatedSql == null ? "" : generatedSql),
                    "AI SQL agent failed safely",
                    false,
                    exception.getMessage(),
                    elapsedMs(startedAt)
            );
            throw exception;
        }
    }

    private AiSqlPlan planSql(AiSecurityContext context, Long branchId, String userQuestion, String conversationContext) {
        String systemPrompt = """
                You create safe PostgreSQL SELECT queries for ValueInSoft.
                Return JSON only with this shape: {"sql":"select ..."}
                Never include Markdown, comments, explanations, semicolons, or more than one statement.
                Never use INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, functions with side effects, or unapproved tables.
                """;
        String userPrompt = """
                User question:
                %s

                Recent conversation context:
                %s

                %s
                """.formatted(
                userQuestion,
                conversationContext == null || conversationContext.isBlank() ? "(none)" : conversationContext,
                schemaCatalog.promptCatalog(context.companyId(), branchId)
        );

        AiModelResponse response = modelClient.generate(new AiModelRequest(systemPrompt, userPrompt, "SQL", ""));
        try {
            String json = stripCodeFence(response.answer());
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String sql = object.has("sql") && !object.get("sql").isJsonNull()
                    ? object.get("sql").getAsString()
                    : "";
            return new AiSqlPlan(sql);
        } catch (RuntimeException exception) {
            log.warn("AI SQL plan parse failed. Raw response length={}",
                    response.answer() == null ? 0 : response.answer().length());
            throw new AiSqlValidationException("The model did not return a valid SQL plan.");
        }
    }

    private String summarize(String userQuestion, String conversationContext, String sql, List<Map<String, Object>> rows) {
        String systemPrompt = """
                You answer ValueInSoft business questions from already-executed read-only SQL results.
                Do not mention SQL unless the user asks.
                If rows are empty, say no matching data was found.
                Keep the answer concise.
                """;
        String userPrompt = """
                User question:
                %s

                Recent conversation context:
                %s

                Validated SQL that was executed:
                %s

                Result rows as JSON:
                %s
                """.formatted(
                userQuestion,
                conversationContext == null || conversationContext.isBlank() ? "(none)" : conversationContext,
                sql,
                gson.toJson(rows)
        );
        AiModelResponse response = modelClient.generate(new AiModelRequest(systemPrompt, userPrompt, "SQL_SUMMARY", ""));
        return response.answer();
    }

    private String stripCodeFence(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }
        return text.trim();
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private String oneLine(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 2_000 ? normalized : normalized.substring(0, 2_000) + "...";
    }

    public record AiSqlAnswer(
            String answer,
            String sql,
            int rowCount
    ) {
    }
}
