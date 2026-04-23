package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvCompany;
import com.example.valueinsoftbackend.DatabaseRequests.DbDataVisualization.Sales.DbDvSales;
import com.example.valueinsoftbackend.DatabaseRequests.DbBranch;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Branch;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DVSalesYearly;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvCompanyChartSalesIncome;
import com.example.valueinsoftbackend.Model.DataVisualizationModels.DvSales;
import com.example.valueinsoftbackend.Model.Request.CompanySalesWindowRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfMonthRequest;
import com.example.valueinsoftbackend.Model.Request.SalesOfYearRequest;
import com.example.valueinsoftbackend.Model.Request.SalesProductsByPeriodRequest;
import com.example.valueinsoftbackend.Model.Sales.SalesProduct;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.example.valueinsoftbackend.util.RequestDateParser;
import com.example.valueinsoftbackend.util.TenantSqlIdentifiers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SalesAnalyticsService {

    private final DbDvSales dbDvSales;
    private final DbDvCompany dbDvCompany;
    private final DbBranch dbBranch;
    private final int salesPdfMaxRows;

    public SalesAnalyticsService(DbDvSales dbDvSales,
                                 DbDvCompany dbDvCompany,
                                 DbBranch dbBranch,
                                 @Value("${sales.products.pdf.max-rows:3000}") int salesPdfMaxRows) {
        this.dbDvSales = dbDvSales;
        this.dbDvCompany = dbDvCompany;
        this.dbBranch = dbBranch;
        this.salesPdfMaxRows = Math.max(salesPdfMaxRows, 100);
    }

    public List<DvSales> getMonthlySales(SalesOfMonthRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        RequestDateParser.parseSqlDate(request.getCurrentMonth(), "currentMonth");
        return dbDvSales.getMonthlySales(request.getCompanyId(), request.getCurrentMonth().trim(), request.getBranchId());
    }

    public List<DVSalesYearly> getYearlySales(SalesOfYearRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        return dbDvSales.getYearlySales(request.getCompanyId(), request.getYear(), request.getBranchId());
    }

    public List<SalesProduct> getSalesProductsByPeriod(SalesProductsByPeriodRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        TenantSqlIdentifiers.requirePositive(request.getBranchId(), "branchId");
        RequestDateParser.parseSqlDate(request.getStartTime(), "startTime");
        RequestDateParser.parseSqlDate(request.getEndTime(), "endTime");
        return dbDvSales.getSalesProductsByPeriod(
                request.getCompanyId(),
                request.getBranchId(),
                request.getStartTime().trim(),
                request.getEndTime().trim()
        );
    }

    public void writeSalesProductsPdf(SalesProductsByPeriodRequest request, OutputStream outputStream) throws IOException {
        List<SalesProduct> rows = getSalesProductsByPeriod(request);
        if (rows.size() > salesPdfMaxRows) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SALES_PRODUCTS_PDF_LIMIT_EXCEEDED",
                    "PDF export is limited to " + salesPdfMaxRows + " rows. Narrow the date range before exporting."
            );
        }

        String html = buildSalesProductsPdfHtml(request, rows);
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
        builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
        builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);
        registerPdfFonts(builder);
        builder.withHtmlContent(html, null);
        builder.toStream(outputStream);
        builder.run();
    }

    public List<Map<String, Object>> getCompanySalesWindow(CompanySalesWindowRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        ArrayList<Branch> branchArrayList = new ArrayList<>(dbBranch.getBranchByCompanyId(request.getCompanyId()));
        return dbDvCompany.getShiftTotalAndIncomeOfAllBranches(request.getCompanyId(), branchArrayList, request.getHours().trim());
    }

    public List<DvCompanyChartSalesIncome> getCompanySalesWindowPerDay(CompanySalesWindowRequest request) {
        TenantSqlIdentifiers.requirePositive(request.getCompanyId(), "companyId");
        ArrayList<Branch> branchArrayList = new ArrayList<>(dbBranch.getBranchByCompanyId(request.getCompanyId()));
        return dbDvCompany.getShiftTotalAndIncomeOfAllBranchesPerDay(request.getCompanyId(), branchArrayList, request.getHours().trim());
    }

    private String buildSalesProductsPdfHtml(SalesProductsByPeriodRequest request, List<SalesProduct> rows) {
        long totalSales = rows.stream().mapToLong(SalesProduct::getSumTotal).sum();
        long totalQuantity = rows.stream().mapToLong(SalesProduct::getSumQuantity).sum();
        long totalOrders = rows.stream().mapToLong(SalesProduct::getNumOfOrder).sum();

        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <head>
                  <meta charset="utf-8" />
                  <style>
                    body { font-family: 'SalesPdfArabic', Tahoma, Arial, sans-serif; font-size: 10px; color: #17202a; }
                    h1 { margin: 0 0 8px 0; font-size: 18px; }
                    .meta { margin-bottom: 12px; }
                    .summary { margin-bottom: 16px; }
                    .summary td { padding: 4px 8px; border: 1px solid #d5d8dc; }
                    table.report { width: 100%; border-collapse: collapse; }
                    table.report th { background: #1f4e78; color: #fff; padding: 6px; border: 1px solid #d5d8dc; }
                    table.report td { padding: 5px; border: 1px solid #d5d8dc; }
                    .right { text-align: right; }
                    .product-name { text-align: start; unicode-bidi: plaintext; }
                    .ltr { direction: ltr; unicode-bidi: embed; }
                  </style>
                </head>
                <body>
                """);
        html.append("<h1>Sales By Product Report</h1>");
        html.append("<div class=\"meta ltr\">")
                .append("Branch Id: ").append(request.getBranchId())
                .append(" | Date Range: ").append(escapeHtml(request.getStartTime().trim()))
                .append(" to ").append(escapeHtml(request.getEndTime().trim()))
                .append("</div>");

        html.append("<table class=\"summary\">")
                .append("<tr><td><b>Product Rows</b></td><td>").append(rows.size()).append("</td><td><b>Total Orders</b></td><td>").append(totalOrders).append("</td></tr>")
                .append("<tr><td><b>Total Quantity</b></td><td>").append(totalQuantity).append("</td><td><b>Total Sales</b></td><td>").append(totalSales).append("</td></tr>")
                .append("</table>");

        html.append("""
                <table class="report">
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th>Orders</th>
                      <th>Quantity</th>
                      <th>Sales</th>
                    </tr>
                  </thead>
                  <tbody>
                """);

        for (SalesProduct row : rows) {
            html.append("<tr>")
                    .append("<td class=\"product-name\" dir=\"auto\">").append(escapeHtml(row.getItemName())).append("</td>")
                    .append("<td class=\"right\">").append(row.getNumOfOrder()).append("</td>")
                    .append("<td class=\"right\">").append(row.getSumQuantity()).append("</td>")
                    .append("<td class=\"right\">").append(row.getSumTotal()).append("</td>")
                    .append("</tr>");
        }

        html.append("""
                  </tbody>
                </table>
                </body>
                </html>
                """);
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

    private void registerPdfFonts(PdfRendererBuilder builder) {
        File primaryFont = findReadableFont(
                Path.of("C:\\Windows\\Fonts\\tahoma.ttf"),
                Path.of("C:\\Windows\\Fonts\\arial.ttf"),
                Path.of("C:\\Windows\\Fonts\\segoeui.ttf")
        );
        if (primaryFont != null) {
            builder.useFont(primaryFont, "SalesPdfArabic");
        }
    }

    private File findReadableFont(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return candidate.toFile();
            }
        }
        return null;
    }
}
