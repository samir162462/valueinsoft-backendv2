package com.example.valueinsoftbackend.Service.product;

import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Product;
import com.example.valueinsoftbackend.Model.Request.ProductCatalogExportRequest;
import com.example.valueinsoftbackend.Model.ResponseModel.ResponsePagination;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.util.PageHandler;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ProductCatalogExportService {

    private static final String[] HEADERS = {
            "Product Id",
            "Product Name",
            "Barcode / Serial",
            "Business Line",
            "Template",
            "Supplier Id",
            "Major",
            "Type",
            "State",
            "Quantity",
            "Buying Price",
            "Lowest Price",
            "Retail Price",
            "Company Name",
            "Buying Day",
            "Tracking Type",
            "SKU",
            "Available Units"
    };

    private static final String[] ARABIC_HEADERS = {
            "معرف المنتج",
            "اسم المنتج",
            "الباركود / السيريال",
            "خط الاعمال",
            "القالب",
            "معرف المورد",
            "التصنيف الرئيسي",
            "النوع",
            "الحالة",
            "الكمية",
            "سعر الشراء",
            "أدنى سعر",
            "سعر البيع",
            "اسم الشركة",
            "تاريخ الشراء",
            "نوع التتبع",
            "رمز الصنف (SKU)",
            "الوحدات المتاحة"
    };

    private final ProductService productService;
    private final AuthorizationService authorizationService;
    private final int exportMaxRows;
    private final int pdfMaxRows;

    public ProductCatalogExportService(ProductService productService,
                                       AuthorizationService authorizationService,
                                       @Value("${inventory.catalog.export.max-rows:50000}") int exportMaxRows,
                                       @Value("${inventory.catalog.export.pdf.max-rows:5000}") int pdfMaxRows) {
        this.productService = productService;
        this.authorizationService = authorizationService;
        this.exportMaxRows = Math.max(exportMaxRows, 100);
        this.pdfMaxRows = Math.max(pdfMaxRows, 100);
    }

    public void writeExcel(String authenticatedName, ProductCatalogExportRequest request, OutputStream outputStream) throws IOException {
        List<Product> products = loadProducts(authenticatedName, request);
        boolean isArabic = request.getLocale() != null && request.getLocale().startsWith("ar");
        boolean isRtl = "rtl".equalsIgnoreCase(request.getDirection());
        String[] currentHeaders = isArabic ? ARABIC_HEADERS : HEADERS;

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet(isArabic ? "سجل المخزون" : "Inventory Catalog");
            sheet.setRightToLeft(isRtl);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            titleRow.createCell(0).setCellValue(isArabic ? "تصدير سجل المخزون" : "Inventory Catalog Export");
            titleRow.getCell(0).setCellStyle(titleStyle);

            Row metaRow = sheet.createRow(rowIndex++);
            metaRow.createCell(0).setCellValue(isArabic ? "معرف الشركة" : "Company Id");
            metaRow.createCell(1).setCellValue(request.getCompanyId());
            metaRow.createCell(2).setCellValue(isArabic ? "معرف الفرع" : "Branch Id");
            metaRow.createCell(3).setCellValue(request.getBranchId());
            metaRow.createCell(4).setCellValue(isArabic ? "الصفوف" : "Rows");
            metaRow.createCell(5).setCellValue(products.size());

            Row filterRow = sheet.createRow(rowIndex++);
            filterRow.createCell(0).setCellValue(isArabic ? "البحث" : "Search");
            filterRow.createCell(1).setCellValue(displaySearch(request));
            filterRow.createCell(2).setCellValue(isArabic ? "خط الاعمال" : "Business Line");
            filterRow.createCell(3).setCellValue(blankToAll(request.getBusinessLineKey(), isArabic));
            filterRow.createCell(4).setCellValue(isArabic ? "القالب" : "Template");
            filterRow.createCell(5).setCellValue(blankToAll(request.getTemplateKey(), isArabic));

            rowIndex++;
            Row headerRow = sheet.createRow(rowIndex++);
            for (int index = 0; index < currentHeaders.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(currentHeaders[index]);
                cell.setCellStyle(headerStyle);
            }

            for (Product product : products) {
                writeProductRow(sheet.createRow(rowIndex++), product);
            }

            for (int columnIndex = 0; columnIndex < currentHeaders.length; columnIndex++) {
                sheet.trackColumnForAutoSizing(columnIndex);
                sheet.autoSizeColumn(columnIndex);
            }


            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    public void writePdf(String authenticatedName, ProductCatalogExportRequest request, OutputStream outputStream) throws IOException {
        List<Product> products = loadProducts(authenticatedName, request);
        if (products.size() > pdfMaxRows) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVENTORY_CATALOG_PDF_LIMIT_EXCEEDED",
                    "PDF export is limited to " + pdfMaxRows + " rows. Use Excel export for larger datasets."
            );
        }

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        try {
            byte[] tajawalRegular = getClass().getResourceAsStream("/fonts/Tajawal-Regular.ttf").readAllBytes();
            builder.useFont(() -> new java.io.ByteArrayInputStream(tajawalRegular), "Tajawal", 400, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
        } catch (Exception e) {
            System.err.println("Failed to load Tajawal-Regular.ttf: " + e.getMessage());
        }
        try {
            byte[] tajawalBold = getClass().getResourceAsStream("/fonts/Tajawal-Bold.ttf").readAllBytes();
            builder.useFont(() -> new java.io.ByteArrayInputStream(tajawalBold), "Tajawal", 700, com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle.NORMAL, true);
        } catch (Exception e) {
            System.err.println("Failed to load Tajawal-Bold.ttf: " + e.getMessage());
        }

        builder.useUnicodeBidiSplitter(new com.openhtmltopdf.bidi.support.ICUBidiSplitter.ICUBidiSplitterFactory());
        builder.useUnicodeBidiReorderer(new com.openhtmltopdf.bidi.support.ICUBidiReorderer());

        builder.withHtmlContent(buildPdfHtml(products, request), null);
        builder.toStream(outputStream);
        builder.run();
    }

    private List<Product> loadProducts(String authenticatedName, ProductCatalogExportRequest request) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                "inventory.item.read"
        );

        List<Product> products = switch (normalizeSearchType(request.getSearchType())) {
            case "dir" -> pagedProducts(productService.searchProductsByText(
                    normalizedText(request).split("\\s+"),
                    String.valueOf(request.getBranchId()),
                    request.getCompanyId(),
                    request.getProductFilter(),
                    new PageHandler("productId", 1, exportMaxRows)
            ));
            case "comName" -> pagedProducts(productService.searchProductsByCompanyName(
                    normalizedText(request),
                    String.valueOf(request.getBranchId()),
                    request.getCompanyId(),
                    request.getProductFilter(),
                    new PageHandler("productId", 1, exportMaxRows)
            ));
            case "Barcode" -> productService.getProductsByBarcode(
                    normalizedText(request),
                    String.valueOf(request.getBranchId()),
                    request.getCompanyId()
            );
            case "allData" -> productService.getProductsAllRange(
                    String.valueOf(request.getBranchId()),
                    request.getCompanyId(),
                    request.getProductFilter()
            );
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_EXPORT_SEARCH_TYPE", "Search type is not supported for inventory export");
        };

        return applyUiFilters(products, request);
    }

    private List<Product> applyUiFilters(List<Product> products, ProductCatalogExportRequest request) {
        String businessLineKey = trimToNull(request.getBusinessLineKey());
        String templateKey = trimToNull(request.getTemplateKey());
        if (businessLineKey == null && templateKey == null) {
            return products == null ? List.of() : products;
        }

        List<Product> filtered = new ArrayList<>();
        for (Product product : products == null ? List.<Product>of() : products) {
            if (businessLineKey != null && !Objects.equals(businessLineKey, product.getBusinessLineKey())) {
                continue;
            }
            if (templateKey != null && !Objects.equals(templateKey, product.getTemplateKey())) {
                continue;
            }
            filtered.add(product);
        }
        return filtered;
    }

    private List<Product> pagedProducts(ResponsePagination<Product> response) {
        if (response == null || response.getProducts() == null) {
            return List.of();
        }
        return response.getProducts();
    }

    private void writeProductRow(Row row, Product product) {
        int cell = 0;
        row.createCell(cell++).setCellValue(product.getProductId());
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getProductName()));
        row.createCell(cell++).setCellValue(safeSpreadsheetText(firstText(product.getBarcode(), product.getSerial(), join(product.getUnitIdentifiers()))));
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getBusinessLineKey()));
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getTemplateKey()));
        row.createCell(cell++).setCellValue(product.getSupplierId());
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getMajor()));
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getType()));
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getPState()));
        row.createCell(cell++).setCellValue(product.getQuantity());
        row.createCell(cell++).setCellValue(product.getBPrice());
        row.createCell(cell++).setCellValue(product.getLPrice());
        row.createCell(cell++).setCellValue(product.getRPrice());
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getCompanyName()));
        row.createCell(cell++).setCellValue(formatTimestamp(product.getBuyingDay()));
        row.createCell(cell++).setCellValue(product.getTrackingType() == null ? "" : product.getTrackingType().name());
        row.createCell(cell++).setCellValue(safeSpreadsheetText(product.getSku()));
        row.createCell(cell).setCellValue(safeSpreadsheetText(join(product.getUnitIdentifiers())));
    }

    private String buildPdfHtml(List<Product> products, ProductCatalogExportRequest request) {
        boolean isArabic = request.getLocale() != null && request.getLocale().startsWith("ar");
        String[] currentHeaders = isArabic ? ARABIC_HEADERS : HEADERS;
        String direction = "rtl".equalsIgnoreCase(request.getDirection()) ? "rtl" : "ltr";
        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <head>
                  <meta charset="utf-8" />
                  <style>
                    @page { size: A4 landscape; margin: 14mm; }
                    body { font-family: 'Tajawal', Arial, sans-serif; font-size: 9px; color: #17202a; }
                    h1 { margin: 0 0 8px 0; font-size: 18px; font-weight: bold; }
                    .meta { margin-bottom: 12px; color: #34495e; }
                    table { width: 100%%; border-collapse: collapse; }
                    th { background: #1f4e78; color: #fff; padding: 5px; border: 1px solid #d5d8dc; font-weight: bold; }
                    td { padding: 4px; border: 1px solid #d5d8dc; vertical-align: top; }
                    .right { text-align: %s; }
                  </style>
                </head>
                """.formatted("rtl".equals(direction) ? "left" : "right"));
        html.append("<body dir=\"").append(direction).append("\">");
        html.append("<h1>").append(isArabic ? "تصدير سجل المخزون" : "Inventory Catalog Export").append("</h1>");
        html.append("<div class=\"meta\">")
                .append(isArabic ? "معرف الشركة: " : "Company Id: ").append(request.getCompanyId())
                .append(isArabic ? " | معرف الفرع: " : " | Branch Id: ").append(request.getBranchId())
                .append(isArabic ? " | الصفوف: " : " | Rows: ").append(products.size())
                .append(isArabic ? " | البحث: " : " | Search: ").append(escapeHtml(displaySearch(request)))
                .append(isArabic ? " | خط الاعمال: " : " | Business Line: ").append(escapeHtml(blankToAll(request.getBusinessLineKey(), isArabic)))
                .append(isArabic ? " | القالب: " : " | Template: ").append(escapeHtml(blankToAll(request.getTemplateKey(), isArabic)))
                .append("</div>");

        html.append("<table><thead><tr>");
        for (String header : currentHeaders) {
            html.append("<th>").append(escapeHtml(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (Product product : products) {
            html.append("<tr>")
                    .append("<td class=\"right\">").append(product.getProductId()).append("</td>")
                    .append("<td>").append(escapeHtml(product.getProductName())).append("</td>")
                    .append("<td>").append(escapeHtml(firstText(product.getBarcode(), product.getSerial(), join(product.getUnitIdentifiers())))).append("</td>")
                    .append("<td>").append(escapeHtml(product.getBusinessLineKey())).append("</td>")
                    .append("<td>").append(escapeHtml(product.getTemplateKey())).append("</td>")
                    .append("<td class=\"right\">").append(product.getSupplierId()).append("</td>")
                    .append("<td>").append(escapeHtml(product.getMajor())).append("</td>")
                    .append("<td>").append(escapeHtml(product.getType())).append("</td>")
                    .append("<td>").append(escapeHtml(product.getPState())).append("</td>")
                    .append("<td class=\"right\">").append(product.getQuantity()).append("</td>")
                    .append("<td class=\"right\">").append(product.getBPrice()).append("</td>")
                    .append("<td class=\"right\">").append(product.getLPrice()).append("</td>")
                    .append("<td class=\"right\">").append(product.getRPrice()).append("</td>")
                    .append("<td>").append(escapeHtml(product.getCompanyName())).append("</td>")
                    .append("<td>").append(escapeHtml(formatTimestamp(product.getBuyingDay()))).append("</td>")
                    .append("<td>").append(product.getTrackingType() == null ? "" : product.getTrackingType().name()).append("</td>")
                    .append("<td>").append(escapeHtml(product.getSku())).append("</td>")
                    .append("<td>").append(escapeHtml(join(product.getUnitIdentifiers()))).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private String normalizeSearchType(String searchType) {
        String value = trimToNull(searchType);
        return value == null ? "allData" : value;
    }

    private String normalizedText(ProductCatalogExportRequest request) {
        String text = trimToNull(request.getText());
        return text == null ? "" : text;
    }

    private String displaySearch(ProductCatalogExportRequest request) {
        String text = normalizedText(request);
        return normalizeSearchType(request.getSearchType()) + (text.isBlank() ? "" : " / " + text);
    }

    private String blankToAll(String value, boolean isArabic) {
        String clean = trimToNull(value);
        return clean == null ? (isArabic ? "الكل" : "All") : clean;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private String firstText(String... values) {
        for (String value : values) {
            String clean = trimToNull(value);
            if (clean != null) {
                return clean;
            }
        }
        return "";
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(", ", values);
    }

    private String formatTimestamp(Timestamp value) {
        if (value == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(value.toLocalDateTime());
    }

    private String safeSpreadsheetText(String value) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
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
}
