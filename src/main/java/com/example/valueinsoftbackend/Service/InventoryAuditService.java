package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryAudit.DbInventoryAuditReadModels;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditRow;
import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditSummary;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryAuditService {

    private final DbInventoryAuditReadModels dbInventoryAuditReadModels;
    private final AuthorizationService authorizationService;
    private final int pdfMaxRows;

    public InventoryAuditService(DbInventoryAuditReadModels dbInventoryAuditReadModels,
                                 AuthorizationService authorizationService,
                                 @Value("${inventory.audit.pdf.max-rows:5000}") int pdfMaxRows) {
        this.dbInventoryAuditReadModels = dbInventoryAuditReadModels;
        this.authorizationService = authorizationService;
        this.pdfMaxRows = Math.max(pdfMaxRows, 100);
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

            for (int columnIndex = 0; columnIndex < 11; columnIndex++) {
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
        excelRow.createCell(1).setCellValue(row.getProductName() == null ? "" : row.getProductName());
        excelRow.createCell(2).setCellValue(row.getCategory() == null ? "" : row.getCategory());
        excelRow.createCell(3).setCellValue(row.getBranch() == null ? "" : row.getBranch());

        writeNumericCell(excelRow, 4, row.getOpeningQty(), numericStyle);
        writeNumericCell(excelRow, 5, row.getInQty(), numericStyle);
        writeNumericCell(excelRow, 6, row.getOutQty(), numericStyle);
        writeNumericCell(excelRow, 7, row.getClosingQty(), numericStyle);
        writeNumericCell(excelRow, 8, row.getUnitPrice(), numericStyle);
        writeNumericCell(excelRow, 9, row.getTotalValue(), numericStyle);
        excelRow.createCell(10).setCellValue(formatTimestamp(row.getLastMovementDate()));
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
                request.getGroupBy(),
                1,
                pdfMaxRows,
                request.getSortField(),
                request.getSortDirection()
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
