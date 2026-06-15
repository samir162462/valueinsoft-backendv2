package com.example.valueinsoftbackend.Service.inventory;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryAudit.DbInventoryAuditReadModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditAiAnalysisResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditAiInsight;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditRow;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditSummary;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.ai.config.AiProperties;
import com.example.valueinsoftbackend.ai.service.AiModelClient;
import com.example.valueinsoftbackend.ai.service.AiModelRequest;
import com.example.valueinsoftbackend.ai.service.AiModelResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class InventoryAuditService {

    private final DbInventoryAuditReadModels dbInventoryAuditReadModels;
    private final AuthorizationService authorizationService;
    private final AiModelClient aiModelClient;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final int pdfMaxRows;
    private final int aiMaxRows;

    public InventoryAuditService(DbInventoryAuditReadModels dbInventoryAuditReadModels,
                                 AuthorizationService authorizationService,
                                 AiModelClient aiModelClient,
                                 AiProperties aiProperties,
                                 ObjectMapper objectMapper,
                                 @Value("${inventory.audit.pdf.max-rows:5000}") int pdfMaxRows,
                                 @Value("${inventory.audit.ai.max-rows:500}") int aiMaxRows) {
        this.dbInventoryAuditReadModels = dbInventoryAuditReadModels;
        this.authorizationService = authorizationService;
        this.aiModelClient = aiModelClient;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.pdfMaxRows = Math.max(pdfMaxRows, 100);
        this.aiMaxRows = Math.max(aiMaxRows, 50);
    }

    public InventoryAuditPageResponse search(String authenticatedName, InventoryAuditSearchRequest request) {
        authorize(authenticatedName, request);
        validateDates(request);
        return dbInventoryAuditReadModels.search(request);
    }

    public void writeExcel(String authenticatedName, InventoryAuditSearchRequest request, OutputStream outputStream) throws IOException {
        authorize(authenticatedName, request);
        validateDates(request);

        InventoryAuditSummary summary = dbInventoryAuditReadModels.fetchSummary(request);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet("Inventory Audit");
            DataFormat dataFormat = workbook.createDataFormat();

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle labelStyle = workbook.createCellStyle();
            Font labelFont = workbook.createFont();
            labelFont.setBold(true);
            labelStyle.setFont(labelFont);

            CellStyle numericStyle = workbook.createCellStyle();
            numericStyle.setDataFormat(dataFormat.getFormat("#,##0"));

            int rowIndex = 0;
            rowIndex = writeSummarySection(sheet, rowIndex, summary, request, labelStyle, numericStyle);
            rowIndex += 1;
            rowIndex = writeHeaderRow(sheet, rowIndex, headerStyle);

            AtomicInteger dataRowIndex = new AtomicInteger(rowIndex);
            dbInventoryAuditReadModels.streamRows(request, row -> writeAuditDataRow(sheet, dataRowIndex.getAndIncrement(), row, numericStyle));

            for (int columnIndex = 0; columnIndex < 14; columnIndex++) {
                sheet.trackColumnForAutoSizing(columnIndex);
                sheet.autoSizeColumn(columnIndex);
            }

            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    public void writePdf(String authenticatedName, InventoryAuditSearchRequest request, OutputStream outputStream) throws IOException {
        authorize(authenticatedName, request);
        validateDates(request);

        InventoryAuditPageResponse fullResult = dbInventoryAuditReadModels.search(toPdfRequest(request));
        if (fullResult.getTotalItems() > pdfMaxRows) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVENTORY_AUDIT_PDF_LIMIT_EXCEEDED",
                    "PDF export is limited to " + pdfMaxRows + " rows. Use Excel export for larger datasets."
            );
        }

        String html = buildPdfHtml(fullResult, request);
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(outputStream);
        builder.run();
    }

    public InventoryAuditAiAnalysisResponse analyzeWithAi(String authenticatedName, InventoryAuditSearchRequest request) {
        authorize(authenticatedName, request);
        validateDates(request);

        InventoryAuditSummary summary = dbInventoryAuditReadModels.fetchSummary(request);
        ArrayList<InventoryAuditRow> rows = collectAnalysisRows(request);
        InventoryAuditAiAnalysisResponse fallback = buildFallbackAnalysis(request, summary, rows);

        if (!aiProperties.isEnabled() || rows.isEmpty()) {
            return fallback;
        }

        try {
            AiModelResponse modelResponse = aiModelClient.generate(new AiModelRequest(
                    buildAiSystemPrompt(request),
                    buildAiUserPrompt(request, summary, rows),
                    "inventory-audit-analysis",
                    ""
            ));

            if (modelResponse.fallback() || modelResponse.answer() == null || modelResponse.answer().isBlank()) {
                return fallback;
            }

            InventoryAuditAiAnalysisResponse parsed = parseAiAnalysis(modelResponse.answer());
            enrichAnalysisMetadata(parsed, modelResponse.modelName(), modelResponse.providerName(), modelResponse.providerCode(), true, rows.size());
            ensureAnalysisDefaults(parsed, fallback);
            return parsed;
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private ArrayList<InventoryAuditRow> collectAnalysisRows(InventoryAuditSearchRequest request) {
        ArrayList<InventoryAuditRow> rows = new ArrayList<>();
        dbInventoryAuditReadModels.streamRows(request, row -> {
            if (rows.size() < aiMaxRows) {
                rows.add(row);
            }
        });
        return rows;
    }

    private String buildAiSystemPrompt(InventoryAuditSearchRequest request) {
        String languageInstruction = isRtlRequest(request)
                ? "Write every user-facing string in Arabic. Use professional Egyptian-friendly business Arabic. Keep riskLevel and severity enum values in English."
                : "Write every user-facing string in English. Keep riskLevel and severity enum values in English.";
        return """
                You are an inventory audit analyst for ValueInSoft.
                Analyze only the provided audit export data. Do not invent rows, products, suppliers, or amounts.
                Return strict JSON only, no markdown and no explanation outside JSON.
                Use concise operational language suitable for a stock controller.
                %s
                Include one short motivational quote in clerkQuote, written as a professional inventory clerk who values accuracy and discipline.
                JSON schema:
                {
                  "headline": "string",
                  "executiveSummary": "string",
                  "clerkQuote": "string",
                  "riskLevel": "LOW|MEDIUM|HIGH",
                  "score": 0,
                  "highlights": [{"title":"string","detail":"string","severity":"LOW|MEDIUM|HIGH","metric":"string"}],
                  "risks": [{"title":"string","detail":"string","severity":"LOW|MEDIUM|HIGH","metric":"string"}],
                  "recommendations": [{"title":"string","detail":"string","severity":"LOW|MEDIUM|HIGH","metric":"string"}],
                  "serialFindings": [{"title":"string","detail":"string","severity":"LOW|MEDIUM|HIGH","metric":"string"}]
                }
                """.formatted(languageInstruction);
    }

    private String buildAiUserPrompt(InventoryAuditSearchRequest request,
                                     InventoryAuditSummary summary,
                                     List<InventoryAuditRow> rows) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("filters", Map.of(
                "companyId", request.getCompanyId(),
                "branchId", request.getBranchId(),
                "fromDate", request.getFromDate().toString(),
                "toDate", request.getToDate().toString(),
                "query", request.getQuery() == null ? "" : request.getQuery(),
                "lowStockOnly", Boolean.TRUE.equals(request.getLowStockOnly()),
                "lowStockThreshold", request.getLowStockThreshold() == null ? "" : request.getLowStockThreshold(),
                "locale", request.getLocale() == null ? "" : request.getLocale(),
                "direction", request.getDirection() == null ? "" : request.getDirection()
        ));
        payload.put("summary", Map.of(
                "totalRows", summary.getTotalRows(),
                "openingQty", summary.getTotalOpeningQty(),
                "inQty", summary.getTotalInQty(),
                "outQty", summary.getTotalOutQty(),
                "closingQty", summary.getTotalClosingQty(),
                "stockValue", summary.getTotalStockValue(),
                "lowStockCount", summary.getLowStockCount()
        ));
        payload.put("computedSignals", buildComputedSignals(summary, rows));
        payload.put("sampledRows", rows.stream().limit(aiMaxRows).map(this::toAiRow).toList());

        try {
            return "Analyze this inventory audit export dataset and produce the requested JSON.\n"
                    + objectMapper.writeValueAsString(payload);
        } catch (IOException exception) {
            return "Analyze this inventory audit export dataset and produce the requested JSON.\n"
                    + payload.toString();
        }
    }

    private Map<String, Object> buildComputedSignals(InventoryAuditSummary summary, List<InventoryAuditRow> rows) {
        long serializedRows = rows.stream().filter(this::isSerializedRow).count();
        long soldOutRows = rows.stream().filter(row -> safeInt(row.getClosingQty()) <= 0 && safeInt(row.getOutQty()) > 0).count();
        long negativeClosingRows = rows.stream().filter(row -> safeInt(row.getClosingQty()) < 0).count();
        List<String> duplicateSerials = rows.stream()
                .map(InventoryAuditRow::getSerialIdentifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .limit(10)
                .toList();
        List<Map<String, Object>> topValueRows = rows.stream()
                .sorted(Comparator.comparingLong((InventoryAuditRow row) -> safeLong(row.getTotalValue())).reversed())
                .limit(8)
                .map(this::toAiRow)
                .toList();

        Map<String, Object> signals = new LinkedHashMap<>();
        signals.put("rowSampleCount", rows.size());
        signals.put("totalRowsInExport", summary.getTotalRows());
        signals.put("serializedRowsInSample", serializedRows);
        signals.put("soldOutRowsInSample", soldOutRows);
        signals.put("negativeClosingRowsInSample", negativeClosingRows);
        signals.put("duplicateSerialsInSample", duplicateSerials);
        signals.put("topValueRowsInSample", topValueRows);
        return signals;
    }

    private Map<String, Object> toAiRow(InventoryAuditRow row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("productId", row.getProductId());
        value.put("unitId", row.getProductUnitId());
        value.put("tracking", row.getTrackingType());
        value.put("serialOrImei", row.getSerialIdentifier());
        value.put("product", row.getProductName());
        value.put("category", row.getCategory());
        value.put("branch", row.getBranch());
        value.put("opening", safeInt(row.getOpeningQty()));
        value.put("in", safeInt(row.getInQty()));
        value.put("out", safeInt(row.getOutQty()));
        value.put("closing", safeInt(row.getClosingQty()));
        value.put("unitPrice", safeInt(row.getUnitPrice()));
        value.put("totalValue", safeLong(row.getTotalValue()));
        value.put("lastMovement", formatTimestamp(row.getLastMovementDate()));
        return value;
    }

    private InventoryAuditAiAnalysisResponse parseAiAnalysis(String answer) {
        String json = extractJson(answer);
        try {
            return objectMapper.readValue(json, InventoryAuditAiAnalysisResponse.class);
        } catch (IOException exception) {
            throw new IllegalArgumentException("AI audit analysis response was not valid JSON", exception);
        }
    }

    private String extractJson(String answer) {
        String value = answer == null ? "" : answer.trim();
        if (value.startsWith("```")) {
            int firstBrace = value.indexOf('{');
            int lastBrace = value.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return value.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return value.substring(firstBrace, lastBrace + 1);
        }
        return value;
    }

    private InventoryAuditAiAnalysisResponse buildFallbackAnalysis(InventoryAuditSearchRequest request,
                                                                   InventoryAuditSummary summary,
                                                                   List<InventoryAuditRow> rows) {
        boolean rtl = isRtlRequest(request);
        ArrayList<InventoryAuditAiInsight> highlights = new ArrayList<>();
        ArrayList<InventoryAuditAiInsight> risks = new ArrayList<>();
        ArrayList<InventoryAuditAiInsight> recommendations = new ArrayList<>();
        ArrayList<InventoryAuditAiInsight> serialFindings = new ArrayList<>();

        highlights.add(new InventoryAuditAiInsight(
                rtl ? "تغطية الحركة" : "Movement coverage",
                rtl
                        ? "المراجعة تحتوي على " + summary.getTotalRows() + " صف، بإجمالي وارد " + summary.getTotalInQty() + " وصادر " + summary.getTotalOutQty() + "."
                        : "The audit contains " + summary.getTotalRows() + " rows with " + summary.getTotalInQty() + " units in and " + summary.getTotalOutQty() + " units out.",
                "LOW",
                (rtl ? "وارد " : "In ") + summary.getTotalInQty() + (rtl ? " / صادر " : " / Out ") + summary.getTotalOutQty()
        ));
        highlights.add(new InventoryAuditAiInsight(
                rtl ? "قيمة المخزون الختامي" : "Closing stock value",
                rtl
                        ? "قيمة المخزون الختامي حسب الفلاتر الحالية هي " + summary.getTotalStockValue() + "."
                        : "Current closing value for the selected filters is " + summary.getTotalStockValue() + ".",
                "LOW",
                String.valueOf(summary.getTotalStockValue())
        ));

        long negativeClosingRows = rows.stream().filter(row -> safeInt(row.getClosingQty()) < 0).count();
        long soldOutRows = rows.stream().filter(row -> safeInt(row.getClosingQty()) <= 0 && safeInt(row.getOutQty()) > 0).count();
        if (negativeClosingRows > 0) {
            risks.add(new InventoryAuditAiInsight(
                    rtl ? "يوجد مخزون بالسالب" : "Negative stock detected",
                    rtl
                            ? negativeClosingRows + " صف من العينة لديهم كمية ختامية بالسالب ويحتاجون تسوية."
                            : negativeClosingRows + " sampled rows have negative closing quantity and need reconciliation.",
                    "HIGH",
                    String.valueOf(negativeClosingRows)
            ));
        }
        if (summary.getLowStockCount() > 0) {
            risks.add(new InventoryAuditAiInsight(
                    rtl ? "صفوف مخزون منخفض" : "Low stock rows",
                    rtl
                            ? summary.getLowStockCount() + " صف عند أو أقل من حد المخزون المنخفض المحدد."
                            : summary.getLowStockCount() + " rows are at or below the selected low-stock threshold.",
                    "MEDIUM",
                    String.valueOf(summary.getLowStockCount())
            ));
        }
        if (soldOutRows > 0) {
            risks.add(new InventoryAuditAiInsight(
                    rtl ? "وحدات سيريال مباعة بالكامل" : "Sold-out serialized units",
                    rtl
                            ? soldOutRows + " صف من العينة تم استهلاكهم بالكامل بعد البيع."
                            : soldOutRows + " sampled rows are fully consumed after sales.",
                    "MEDIUM",
                    String.valueOf(soldOutRows)
            ));
        }

        List<String> duplicateSerials = rows.stream()
                .map(InventoryAuditRow::getSerialIdentifier)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();
        if (duplicateSerials.isEmpty()) {
            serialFindings.add(new InventoryAuditAiInsight(
                    rtl ? "تتبع السيريال / IMEI" : "Serialized traceability",
                    rtl
                            ? "لم يتم العثور على قيم IMEI أو سيريال مكررة داخل العينة."
                            : "No duplicate IMEI or serial values were found in the sampled rows.",
                    "LOW",
                    rows.stream().filter(this::isSerializedRow).count() + (rtl ? " صف سيريال" : " serialized rows")
            ));
        } else {
            serialFindings.add(new InventoryAuditAiInsight(
                    rtl ? "سيريالات مكررة في العينة" : "Duplicate serials in sample",
                    rtl
                            ? "قيم السيريال/IMEI المكررة تحتاج مراجعة: " + String.join(", ", duplicateSerials)
                            : "Duplicate serial/IMEI values need review: " + String.join(", ", duplicateSerials),
                    "HIGH",
                    String.valueOf(duplicateSerials.size())
            ));
        }

        recommendations.add(new InventoryAuditAiInsight(
                rtl ? "ابدأ بتسوية الاستثناءات" : "Reconcile exceptions first",
                rtl
                        ? "ابدأ بالكميات الختامية السالبة، والسيريالات المكررة، وصفوف المخزون المنخفض قبل الاعتماد على قيمة المخزون في التصدير."
                        : "Start with negative closing quantities, duplicate serials, and low-stock rows before relying on the export for stock value.",
                risks.stream().anyMatch(risk -> "HIGH".equalsIgnoreCase(risk.getSeverity())) ? "HIGH" : "MEDIUM",
                risks.size() + (rtl ? " مؤشرات خطر" : " risk signals")
        ));
        recommendations.add(new InventoryAuditAiInsight(
                rtl ? "استخدم تصدير Excel للمتابعة التفصيلية" : "Use the Excel export for row-level follow-up",
                rtl
                        ? "التحليل يستخدم نفس البيانات المفلترة الخاصة بتصدير Excel. صدّر Excel عند الحاجة لتوزيع المتابعة أو التسويات على مستوى الصف."
                        : "The analysis uses the same filtered dataset as the Excel export. Export Excel when you need to assign row owners or perform bulk reconciliation.",
                "LOW",
                rows.size() + (rtl ? " صف في العينة" : " sampled rows")
        ));

        String riskLevel = negativeClosingRows > 0 || !duplicateSerials.isEmpty()
                ? "HIGH"
                : (summary.getLowStockCount() > 0 || soldOutRows > 0 ? "MEDIUM" : "LOW");
        int score = switch (riskLevel) {
            case "HIGH" -> 52;
            case "MEDIUM" -> 74;
            default -> 91;
        };

        InventoryAuditAiAnalysisResponse response = new InventoryAuditAiAnalysisResponse(
                rtl ? "تحليل مراجعة المخزون" : "Inventory audit analysis",
                rtl
                        ? "تم تحليل فترة المراجعة المحددة باستخدام إجماليات الحركة، وتتبع السيريال/IMEI، وقيمة المخزون، ومؤشرات المخزون المنخفض."
                        : "The selected audit period was analyzed using movement totals, serialized traceability, stock value, and low-stock signals.",
                rtl
                        ? "موظف المخزون المحترف لا يطارد الأرقام في النهاية، بل يحافظ عليها صحيحة من البداية."
                        : "A professional inventory clerk does not chase numbers at the end; they keep them accurate from the start.",
                riskLevel,
                score,
                highlights,
                risks,
                recommendations,
                serialFindings,
                Instant.now(),
                aiProperties.getModel(),
                "",
                "",
                false,
                rows.size()
        );
        ensureAnalysisDefaults(response, null);
        return response;
    }

    private void enrichAnalysisMetadata(InventoryAuditAiAnalysisResponse response,
                                        String modelName,
                                        String providerName,
                                        String providerCode,
                                        boolean aiGenerated,
                                        int rowCountAnalyzed) {
        response.setGeneratedAt(Instant.now());
        response.setModel(modelName == null || modelName.isBlank() ? aiProperties.getModel() : modelName);
        response.setProviderName(providerName == null ? "" : providerName);
        response.setProviderCode(providerCode == null ? "" : providerCode);
        response.setAiGenerated(aiGenerated);
        response.setRowCountAnalyzed(rowCountAnalyzed);
    }

    private void ensureAnalysisDefaults(InventoryAuditAiAnalysisResponse response, InventoryAuditAiAnalysisResponse fallback) {
        if (response.getHeadline() == null || response.getHeadline().isBlank()) {
            response.setHeadline(fallback == null ? "Inventory audit analysis" : fallback.getHeadline());
        }
        if (response.getExecutiveSummary() == null || response.getExecutiveSummary().isBlank()) {
            response.setExecutiveSummary(fallback == null ? "" : fallback.getExecutiveSummary());
        }
        if (response.getClerkQuote() == null || response.getClerkQuote().isBlank()) {
            response.setClerkQuote(fallback == null
                    ? "A professional inventory clerk does not chase numbers at the end; they keep them accurate from the start."
                    : fallback.getClerkQuote());
        }
        String riskLevel = response.getRiskLevel() == null ? "" : response.getRiskLevel().trim().toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) {
            riskLevel = fallback == null ? "LOW" : fallback.getRiskLevel();
        }
        response.setRiskLevel(riskLevel);
        if (response.getScore() == null) {
            response.setScore(fallback == null ? 80 : fallback.getScore());
        }
        response.setScore(Math.max(0, Math.min(100, response.getScore())));
        if (response.getHighlights() == null) {
            response.setHighlights(new ArrayList<>());
        }
        if (response.getRisks() == null) {
            response.setRisks(new ArrayList<>());
        }
        if (response.getRecommendations() == null) {
            response.setRecommendations(new ArrayList<>());
        }
        if (response.getSerialFindings() == null) {
            response.setSerialFindings(new ArrayList<>());
        }
        if (response.getGeneratedAt() == null) {
            response.setGeneratedAt(Instant.now());
        }
        if (response.getModel() == null || response.getModel().isBlank()) {
            response.setModel(aiProperties.getModel());
        }
        if (response.getProviderCode() == null) {
            response.setProviderCode("");
        }
        if (response.getProviderName() == null) {
            response.setProviderName("");
        }
    }

    private boolean isSerializedRow(InventoryAuditRow row) {
        return row.getProductUnitId() != null
                || (row.getTrackingType() != null && !"QUANTITY".equalsIgnoreCase(row.getTrackingType()))
                || (row.getSerialIdentifier() != null && !row.getSerialIdentifier().isBlank());
    }

    private boolean isRtlRequest(InventoryAuditSearchRequest request) {
        if (request == null) {
            return false;
        }
        String direction = request.getDirection() == null ? "" : request.getDirection().trim().toLowerCase(Locale.ROOT);
        String locale = request.getLocale() == null ? "" : request.getLocale().trim().toLowerCase(Locale.ROOT);
        return "rtl".equals(direction) || locale.startsWith("ar");
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private void authorize(String authenticatedName, InventoryAuditSearchRequest request) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "inventory.item.read"
        );
    }

    private void validateDates(InventoryAuditSearchRequest request) {
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "fromDate must be less than or equal to toDate"
            );
        }
    }

    private int writeSummarySection(SXSSFSheet sheet,
                                    int rowIndex,
                                    InventoryAuditSummary summary,
                                    InventoryAuditSearchRequest request,
                                    CellStyle labelStyle,
                                    CellStyle numericStyle) {
        Row titleRow = sheet.createRow(rowIndex++);
        titleRow.createCell(0).setCellValue("Inventory Audit Report");

        Row filtersRow = sheet.createRow(rowIndex++);
        filtersRow.createCell(0).setCellValue("Date Range");
        filtersRow.getCell(0).setCellStyle(labelStyle);
        filtersRow.createCell(1).setCellValue(request.getFromDate() + " to " + request.getToDate());
        filtersRow.createCell(2).setCellValue("Branch Id");
        filtersRow.getCell(2).setCellStyle(labelStyle);
        filtersRow.createCell(3).setCellValue(request.getBranchId());

        Row queryRow = sheet.createRow(rowIndex++);
        queryRow.createCell(0).setCellValue("Query");
        queryRow.getCell(0).setCellStyle(labelStyle);
        queryRow.createCell(1).setCellValue(request.getQuery() == null ? "All" : request.getQuery());
        queryRow.createCell(2).setCellValue("Low Stock Threshold");
        queryRow.getCell(2).setCellStyle(labelStyle);
        queryRow.createCell(3).setCellValue(
                request.getLowStockThreshold() == null
                        ? "N/A"
                        : String.valueOf(request.getLowStockThreshold())
        );

        rowIndex = writeSummaryMetricRow(sheet, rowIndex, "Total Rows", summary.getTotalRows(), "Total Value", summary.getTotalStockValue(), labelStyle, numericStyle);
        rowIndex = writeSummaryMetricRow(sheet, rowIndex, "Opening Qty", summary.getTotalOpeningQty(), "Closing Qty", summary.getTotalClosingQty(), labelStyle, numericStyle);
        rowIndex = writeSummaryMetricRow(sheet, rowIndex, "In Qty", summary.getTotalInQty(), "Out Qty", summary.getTotalOutQty(), labelStyle, numericStyle);
        rowIndex = writeSummaryMetricRow(sheet, rowIndex, "Low Stock Count", summary.getLowStockCount(), "", null, labelStyle, numericStyle);
        return rowIndex;
    }

    private int writeSummaryMetricRow(SXSSFSheet sheet,
                                      int rowIndex,
                                      String leftLabel,
                                      Long leftValue,
                                      String rightLabel,
                                      Long rightValue,
                                      CellStyle labelStyle,
                                      CellStyle numericStyle) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(leftLabel);
        row.getCell(0).setCellStyle(labelStyle);
        Cell leftValueCell = row.createCell(1);
        leftValueCell.setCellValue(leftValue == null ? 0 : leftValue);
        leftValueCell.setCellStyle(numericStyle);

        if (rightLabel != null && !rightLabel.isBlank()) {
            row.createCell(2).setCellValue(rightLabel);
            row.getCell(2).setCellStyle(labelStyle);
            Cell rightValueCell = row.createCell(3);
            rightValueCell.setCellValue(rightValue == null ? 0 : rightValue);
            rightValueCell.setCellStyle(numericStyle);
        }

        return rowIndex;
    }

    private int writeHeaderRow(SXSSFSheet sheet, int rowIndex, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(rowIndex);
        String[] headers = {
                "Product Id",
                "Product Unit Id",
                "Tracking Type",
                "Serial / IMEI",
                "Product Name",
                "Category",
                "Branch",
                "Opening Qty",
                "In Qty",
                "Out Qty",
                "Closing Qty",
                "Unit Price",
                "Total Value",
                "Last Movement Date"
        };

        for (int index = 0; index < headers.length; index++) {
            Cell cell = headerRow.createCell(index);
            cell.setCellValue(headers[index]);
            cell.setCellStyle(headerStyle);
        }

        return rowIndex + 1;
    }

    private void writeAuditDataRow(SXSSFSheet sheet, int rowIndex, InventoryAuditRow row, CellStyle numericStyle) {
        Row excelRow = sheet.createRow(rowIndex);
        excelRow.createCell(0).setCellValue(row.getProductId());
        excelRow.createCell(1).setCellValue(row.getProductUnitId() == null ? "" : String.valueOf(row.getProductUnitId()));
        excelRow.createCell(2).setCellValue(row.getTrackingType() == null ? "" : row.getTrackingType());
        excelRow.createCell(3).setCellValue(row.getSerialIdentifier() == null ? "" : row.getSerialIdentifier());
        excelRow.createCell(4).setCellValue(row.getProductName() == null ? "" : row.getProductName());
        excelRow.createCell(5).setCellValue(row.getCategory() == null ? "" : row.getCategory());
        excelRow.createCell(6).setCellValue(row.getBranch() == null ? "" : row.getBranch());

        writeNumericCell(excelRow, 7, row.getOpeningQty(), numericStyle);
        writeNumericCell(excelRow, 8, row.getInQty(), numericStyle);
        writeNumericCell(excelRow, 9, row.getOutQty(), numericStyle);
        writeNumericCell(excelRow, 10, row.getClosingQty(), numericStyle);
        writeNumericCell(excelRow, 11, row.getUnitPrice(), numericStyle);
        writeNumericCell(excelRow, 12, row.getTotalValue(), numericStyle);
        excelRow.createCell(13).setCellValue(formatTimestamp(row.getLastMovementDate()));
    }

    private void writeNumericCell(Row row, int columnIndex, Number value, CellStyle numericStyle) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value == null ? 0D : value.doubleValue());
        cell.setCellStyle(numericStyle);
    }

    private InventoryAuditSearchRequest toPdfRequest(InventoryAuditSearchRequest request) {
        return new InventoryAuditSearchRequest(
                request.getCompanyId(),
                request.getBranchId(),
                request.getFromDate(),
                request.getToDate(),
                request.getQuery(),
                request.getProductId(),
                request.getCategory(),
                request.getMajor(),
                request.getBusinessLineKey(),
                request.getTemplateKey(),
                request.getSupplierId(),
                request.getLowStockThreshold(),
                request.getLowStockOnly(),
                request.getExcludeSerializedFromLowStock(),
                request.getIncludeOutOfStockInLowStock(),
                request.getGroupBy(),
                1,
                pdfMaxRows,
                request.getSortField(),
                request.getSortDirection(),
                request.getLocale(),
                request.getDirection()
        );
    }

    private String buildPdfHtml(InventoryAuditPageResponse response, InventoryAuditSearchRequest request) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <head>
                  <meta charset="utf-8" />
                  <style>
                    body { font-family: Arial, sans-serif; font-size: 10px; color: #17202a; }
                    h1 { margin: 0 0 8px 0; font-size: 18px; }
                    .meta { margin-bottom: 12px; }
                    .summary { margin-bottom: 16px; }
                    .summary td { padding: 4px 8px; border: 1px solid #d5d8dc; }
                    table.audit { width: 100%; border-collapse: collapse; }
                    table.audit th { background: #1f4e78; color: #fff; padding: 6px; border: 1px solid #d5d8dc; }
                    table.audit td { padding: 5px; border: 1px solid #d5d8dc; }
                    .right { text-align: right; }
                  </style>
                </head>
                <body>
                """);
        html.append("<h1>Inventory Audit Report</h1>");
        html.append("<div class=\"meta\">")
                .append("Branch Id: ").append(request.getBranchId())
                .append(" | Date Range: ").append(escapeHtml(request.getFromDate().toString()))
                .append(" to ").append(escapeHtml(request.getToDate().toString()))
                .append("</div>");

        InventoryAuditSummary summary = response.getSummary();
        html.append("<table class=\"summary\">")
                .append("<tr><td><b>Total Rows</b></td><td>").append(summary.getTotalRows()).append("</td><td><b>Total Stock Value</b></td><td>").append(summary.getTotalStockValue()).append("</td></tr>")
                .append("<tr><td><b>Opening Qty</b></td><td>").append(summary.getTotalOpeningQty()).append("</td><td><b>Closing Qty</b></td><td>").append(summary.getTotalClosingQty()).append("</td></tr>")
                .append("<tr><td><b>In Qty</b></td><td>").append(summary.getTotalInQty()).append("</td><td><b>Out Qty</b></td><td>").append(summary.getTotalOutQty()).append("</td></tr>")
                .append("<tr><td><b>Low Stock Count</b></td><td>").append(summary.getLowStockCount()).append("</td><td></td><td></td></tr>")
                .append("</table>");

        html.append("""
                <table class="audit">
                  <thead>
                    <tr>
                      <th>Product Id</th>
                      <th>Product Unit Id</th>
                      <th>Tracking Type</th>
                      <th>Serial / IMEI</th>
                      <th>Product Name</th>
                      <th>Category</th>
                      <th>Branch</th>
                      <th>Opening Qty</th>
                      <th>In Qty</th>
                      <th>Out Qty</th>
                      <th>Closing Qty</th>
                      <th>Unit Price</th>
                      <th>Total Value</th>
                      <th>Last Movement Date</th>
                    </tr>
                  </thead>
                  <tbody>
                """);

        for (InventoryAuditRow row : response.getRows()) {
            html.append("<tr>")
                    .append("<td>").append(row.getProductId()).append("</td>")
                    .append("<td>").append(row.getProductUnitId() == null ? "" : row.getProductUnitId()).append("</td>")
                    .append("<td>").append(escapeHtml(row.getTrackingType())).append("</td>")
                    .append("<td>").append(escapeHtml(row.getSerialIdentifier())).append("</td>")
                    .append("<td>").append(escapeHtml(row.getProductName())).append("</td>")
                    .append("<td>").append(escapeHtml(row.getCategory())).append("</td>")
                    .append("<td>").append(escapeHtml(row.getBranch())).append("</td>")
                    .append("<td class=\"right\">").append(row.getOpeningQty()).append("</td>")
                    .append("<td class=\"right\">").append(row.getInQty()).append("</td>")
                    .append("<td class=\"right\">").append(row.getOutQty()).append("</td>")
                    .append("<td class=\"right\">").append(row.getClosingQty()).append("</td>")
                    .append("<td class=\"right\">").append(row.getUnitPrice()).append("</td>")
                    .append("<td class=\"right\">").append(row.getTotalValue()).append("</td>")
                    .append("<td>").append(escapeHtml(formatTimestamp(row.getLastMovementDate()))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatTimestamp(Timestamp value) {
        if (value == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(value.toLocalDateTime());
    }
}
