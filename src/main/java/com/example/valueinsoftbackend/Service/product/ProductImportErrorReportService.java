package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportColumn;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportErrorReportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductImportErrorReportService {

    private final ProductImportRepository importRepository;
    private final ProductImportTemplateService templateService;

    public ProductImportErrorReportService(ProductImportRepository importRepository,
                                           ProductImportTemplateService templateService) {
        this.importRepository = importRepository;
        this.templateService = templateService;
    }

    public String buildErrorReportCsv(int companyId, int branchId, long batchId) {
        Map<String, Object> batch = importRepository.findBatch(companyId, batchId);
        if (batch == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_BATCH_NOT_FOUND", "Import batch was not found");
        }
        Number batchBranchId = (Number) batch.get("branch_id");
        if (batchBranchId == null || batchBranchId.intValue() != branchId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_BATCH_BRANCH_MISMATCH", "Import batch does not belong to the selected branch");
        }

        List<String> canonicalColumns = templateService.columns()
                .stream()
                .map(ProductImportColumn::header)
                .toList();
        List<String> headers = new ArrayList<>();
        headers.add("row_number");
        headers.add("status");
        headers.addAll(canonicalColumns);
        headers.add("error_fields");
        headers.add("error_codes");
        headers.add("error_messages");

        StringBuilder csv = new StringBuilder();
        appendCsvLine(csv, headers);
        for (ProductImportErrorReportRow row : importRepository.errorReportRows(companyId, batchId)) {
            List<String> values = new ArrayList<>();
            values.add(String.valueOf(row.rowNumber()));
            values.add(row.status());
            for (String column : canonicalColumns) {
                values.add(row.rawData().getOrDefault(column, ""));
            }
            values.add(joinErrors(row.errors(), ProductImportErrorResponse::fieldName));
            values.add(joinErrors(row.errors(), ProductImportErrorResponse::code));
            values.add(joinErrors(row.errors(), ProductImportErrorResponse::message));
            appendCsvLine(csv, values);
        }
        return csv.toString();
    }

    private String joinErrors(List<ProductImportErrorResponse> errors, ErrorPartExtractor extractor) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        return errors.stream()
                .map(extractor::extract)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private void appendCsvLine(StringBuilder csv, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(sanitizeForSpreadsheet(values.get(i))));
        }
        csv.append("\r\n");
    }

    private String sanitizeForSpreadsheet(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0;
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @FunctionalInterface
    private interface ErrorPartExtractor {
        String extract(ProductImportErrorResponse error);
    }
}
