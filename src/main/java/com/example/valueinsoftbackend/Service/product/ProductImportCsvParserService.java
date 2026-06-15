package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryImport.ParsedProductImportRow;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportColumn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductImportCsvParserService {

    private final ProductImportTemplateService templateService;
    private final int maxRows;
    private final long maxFileSizeBytes;

    public ProductImportCsvParserService(ProductImportTemplateService templateService,
                                         @Value("${inventory.import.max-rows:10000}") int maxRows,
                                         @Value("${inventory.import.max-file-size-bytes:5242880}") long maxFileSizeBytes) {
        this.templateService = templateService;
        this.maxRows = Math.max(1, maxRows);
        this.maxFileSizeBytes = Math.max(1024L, maxFileSizeBytes);
    }

    public List<ParsedProductImportRow> parse(MultipartFile file) {
        validateFile(file);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_EMPTY", "CSV file is empty");
            }

            if (isExcelSeparatorHint(headerLine)) {
                headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_EMPTY", "CSV file is empty");
                }
            }

            List<String> headers = parseLine(headerLine, 1);
            validateHeaders(headers);

            List<ParsedProductImportRow> rows = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.isBlank()) {
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_ROW_LIMIT_EXCEEDED", "CSV row limit exceeded: " + maxRows);
                }
                List<String> values = parseLine(line, rowNumber);
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? values.get(i).trim() : "");
                }
                rows.add(new ParsedProductImportRow(rowNumber, row));
            }
            return rows;
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_READ_FAILED", "Unable to read CSV file");
        }
    }

    private boolean isExcelSeparatorHint(String line) {
        return line != null && line.trim().equalsIgnoreCase("sep=,");
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_FILE_REQUIRED", "CSV file is required");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_FILE_TOO_LARGE",
                    "CSV file must be " + maxFileSizeBytes + " bytes or smaller");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".csv")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_FILE_TYPE_INVALID", "Only .csv files are supported");
        }
    }

    private void validateHeaders(List<String> headers) {
        Set<String> present = new HashSet<>(headers);
        List<String> missing = templateService.columns().stream()
                .filter(ProductImportColumn::required)
                .map(ProductImportColumn::header)
                .filter(header -> !present.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_REQUIRED_HEADER_MISSING",
                    "CSV is missing required headers", missing);
        }
    }

    private List<String> parseLine(String line, int rowNumber) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (inQuotes) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV_QUOTE_UNCLOSED", "Unclosed quoted field at row " + rowNumber);
        }

        values.add(current.toString());
        return values;
    }
}
