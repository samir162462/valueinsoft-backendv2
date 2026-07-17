package com.example.valueinsoftbackend.Service.finance;

import com.example.valueinsoftbackend.DatabaseRequests.DbFinanceDailyCashClosingReport;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingCashMovementRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingHeader;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingPaymentBreakdownRow;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingReportData;
import com.example.valueinsoftbackend.Model.Finance.DailyCashClosingSummary;
import com.example.valueinsoftbackend.Model.Request.Finance.DailyCashClosingReportRequest;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FinanceDailyCashClosingReportService {

    public static final String CAPABILITY = "finance.reports.daily_cash_closing.view";

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final DbFinanceDailyCashClosingReport dbReport;
    private final AuthorizationService authorizationService;
    private final int pdfMaxRows;

    public FinanceDailyCashClosingReportService(DbFinanceDailyCashClosingReport dbReport,
                                                AuthorizationService authorizationService,
                                                @Value("${finance.daily-cash-closing.pdf.max-rows:5000}") int pdfMaxRows) {
        this.dbReport = dbReport;
        this.authorizationService = authorizationService;
        this.pdfMaxRows = Math.max(pdfMaxRows, 100);
    }

    public void writePdf(String authenticatedName,
                         DailyCashClosingReportRequest request,
                         OutputStream outputStream) throws IOException {
        DailyCashClosingReportRequest normalizedRequest = normalizeAndValidate(request);
        authorize(authenticatedName, normalizedRequest);

        DailyCashClosingReportData data = buildReportData(authenticatedName, normalizedRequest);
        if (data.getInvoices().size() > pdfMaxRows || data.getExpenses().size() > pdfMaxRows) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "DAILY_CASH_CLOSING_PDF_LIMIT_EXCEEDED",
                    "PDF export is limited to " + pdfMaxRows + " invoice rows and " + pdfMaxRows + " expense rows."
            );
        }

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(buildPdfHtml(data), null);
        builder.toStream(outputStream);
        builder.run();
    }

    private DailyCashClosingReportData buildReportData(String authenticatedName, DailyCashClosingReportRequest request) {
        DailyCashClosingHeader header = dbReport.fetchHeader(request);
        DbFinanceDailyCashClosingReport.SalesTotals salesTotals = dbReport.fetchSalesTotals(request);
        DbFinanceDailyCashClosingReport.ShiftTotals shiftTotals = dbReport.fetchShiftTotals(request);
        ArrayList<DailyCashClosingPaymentBreakdownRow> paymentBreakdown = dbReport.fetchPaymentBreakdown(request);
        ArrayList<DailyCashClosingCashMovementRow> cashMovements = dbReport.fetchCashMovements(request);
        BigDecimal totalExpenses = scale(dbReport.fetchExpenseTotal(request));

        DailyCashClosingSummary summary = buildSummary(salesTotals, shiftTotals, paymentBreakdown, cashMovements, totalExpenses);

        DailyCashClosingReportData data = new DailyCashClosingReportData();
        data.setCompanyId(request.getCompanyId());
        data.setBranchId(request.getBranchId());
        data.setDateFrom(request.getDateFrom());
        data.setDateTo(request.getDateTo());
        data.setGeneratedBy(authenticatedName);
        data.setGeneratedAt(Instant.now());
        data.setHeader(header);
        data.setSummary(summary);
        data.setPaymentBreakdown(paymentBreakdown);
        data.setCashMovements(cashMovements);
        data.setInvoices(dbReport.fetchInvoices(request, pdfMaxRows + 1));
        data.setExpenses(dbReport.fetchExpenses(request, pdfMaxRows + 1));
        return data;
    }

    private DailyCashClosingSummary buildSummary(DbFinanceDailyCashClosingReport.SalesTotals salesTotals,
                                                 DbFinanceDailyCashClosingReport.ShiftTotals shiftTotals,
                                                 ArrayList<DailyCashClosingPaymentBreakdownRow> paymentBreakdown,
                                                 ArrayList<DailyCashClosingCashMovementRow> cashMovements,
                                                 BigDecimal totalExpenses) {
        BigDecimal cashSales = ZERO;
        BigDecimal cardSales = ZERO;
        BigDecimal walletSales = ZERO;
        BigDecimal creditSales = ZERO;

        for (DailyCashClosingPaymentBreakdownRow row : paymentBreakdown) {
            BigDecimal net = scale(row.getNetAmount());
            switch (PaymentTypeClassifier.classify(row.getPaymentMethod()).category()) {
                case CASH -> cashSales = cashSales.add(net);
                case CARD -> cardSales = cardSales.add(net);
                case WALLET -> walletSales = walletSales.add(net);
                case RECEIVABLE -> creditSales = creditSales.add(net);
                default -> {
                }
            }
        }

        Map<String, DailyCashClosingCashMovementRow> movements = cashMovements.stream()
                .collect(Collectors.toMap(row -> nullToEmpty(row.getMovementType()).toUpperCase(Locale.ROOT), Function.identity(), (left, right) -> left));

        BigDecimal openingCash = firstNonZero(movementAmount(movements, "OPENING_FLOAT"), shiftTotals.openingFloat());
        BigDecimal cashIn = movementAmount(movements, "PAID_IN");
        BigDecimal cashRefunds = movementAmount(movements, "CASH_REFUND");
        BigDecimal cashExpenses = movementAmount(movements, "PAID_OUT");
        BigDecimal cashOut = movementAmount(movements, "SAFE_DROP");
        BigDecimal calculatedExpectedCash = openingCash
                .add(cashSales)
                .add(cashIn)
                .subtract(cashRefunds)
                .subtract(cashExpenses)
                .subtract(cashOut);
        BigDecimal actualCountedCash = firstNonZero(movementAmount(movements, "CLOSE_COUNT"), shiftTotals.countedCash());
        BigDecimal cashDifference = actualCountedCash.subtract(calculatedExpectedCash);

        return new DailyCashClosingSummary(
                scale(salesTotals.grossSales()),
                scale(salesTotals.discounts()),
                scale(salesTotals.returnsAmount()),
                scale(salesTotals.netSales()),
                scale(cashSales),
                scale(cardSales),
                scale(walletSales),
                scale(creditSales),
                scale(totalExpenses),
                scale(openingCash),
                scale(cashIn),
                scale(cashRefunds),
                scale(cashExpenses),
                scale(cashOut),
                scale(calculatedExpectedCash),
                scale(actualCountedCash),
                scale(cashDifference),
                salesTotals.invoiceCount(),
                salesTotals.returnsCount());
    }

    private DailyCashClosingReportRequest normalizeAndValidate(DailyCashClosingReportRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_FILTERS", "Report filters are required");
        }
        if (request.getCompanyId() <= 0 || request.getBranchId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_SCOPE", "companyId and branchId are required");
        }
        if (request.getDateFrom() == null || request.getDateTo() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "dateFrom and dateTo are required");
        }
        if (request.getDateFrom().isAfter(request.getDateTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "dateFrom must be less than or equal to dateTo");
        }
        if (request.getDateFrom().plusDays(31).isBefore(request.getDateTo())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_LARGE", "Daily cash closing PDF is limited to 32 days");
        }
        if (!dbReport.branchBelongsToCompany(request.getCompanyId(), request.getBranchId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BRANCH_NOT_FOUND", "Branch was not found for the selected company");
        }

        request.setCashierId(trimToNull(request.getCashierId()));
        request.setPaymentMethod(trimToNull(request.getPaymentMethod()));
        request.setStatus(trimToNull(request.getStatus()));
        return request;
    }

    private void authorize(String authenticatedName, DailyCashClosingReportRequest request) {
        authorizationService.assertAuthenticatedCapability(
                authenticatedName,
                request.getCompanyId(),
                request.getBranchId(),
                CAPABILITY
        );
    }

    private String buildPdfHtml(DailyCashClosingReportData data) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <head>
                  <meta charset="utf-8" />
                  <style>
                    @page { size: A4 landscape; margin: 12mm; }
                    body { font-family: Arial, sans-serif; font-size: 9px; color: #17202a; }
                    h1 { margin: 0 0 4px 0; font-size: 18px; }
                    h2 { margin: 14px 0 6px 0; font-size: 12px; color: #1f4e78; }
                    .meta { margin: 0 0 10px 0; color: #34495e; line-height: 1.5; }
                    .summary { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
                    .summary td { width: 12.5%; padding: 5px; border: 1px solid #d5d8dc; vertical-align: top; }
                    .summary .label { color: #566573; font-size: 8px; }
                    .summary .value { display: block; margin-top: 2px; font-size: 11px; font-weight: bold; }
                    table.grid { width: 100%; border-collapse: collapse; margin-bottom: 10px; }
                    table.grid th { background: #1f4e78; color: #fff; padding: 5px; border: 1px solid #d5d8dc; }
                    table.grid td { padding: 4px; border: 1px solid #d5d8dc; }
                    .right { text-align: right; }
                    .muted { color: #7f8c8d; }
                    .signatures { width: 100%; margin-top: 18px; border-collapse: collapse; }
                    .signatures td { width: 33%; padding: 18px 8px 4px 8px; border-top: 1px solid #566573; text-align: center; }
                  </style>
                </head>
                <body>
                """);
        appendHeader(html, data);
        appendSummary(html, data.getSummary());
        appendPaymentBreakdown(html, data);
        appendCashMovements(html, data);
        appendInvoices(html, data);
        appendExpenses(html, data);
        appendFooter(html, data);
        html.append("</body></html>");
        return html.toString();
    }

    private void appendHeader(StringBuilder html, DailyCashClosingReportData data) {
        DailyCashClosingHeader header = data.getHeader();
        html.append("<h1>Daily Sales &amp; Cash Closing Report</h1>");
        html.append("<div class=\"meta\">")
                .append("<b>Company:</b> ").append(escapeHtml(header.getCompanyName()))
                .append(" &nbsp; <b>Branch:</b> ").append(escapeHtml(header.getBranchName()))
                .append(" &nbsp; <b>Date range:</b> ").append(data.getDateFrom()).append(" to ").append(data.getDateTo())
                .append("<br/><b>Generated by:</b> ").append(escapeHtml(data.getGeneratedBy()))
                .append(" &nbsp; <b>Generated at:</b> ").append(formatInstant(data.getGeneratedAt()));
        if (header.getCashierName() != null) {
            html.append(" &nbsp; <b>Cashier:</b> ").append(escapeHtml(header.getCashierName()));
        }
        if (header.getShiftLabel() != null) {
            html.append(" &nbsp; <b>Shift:</b> ").append(escapeHtml(header.getShiftLabel()));
        }
        html.append("</div>");
    }

    private void appendSummary(StringBuilder html, DailyCashClosingSummary summary) {
        html.append("<h2>Summary</h2><table class=\"summary\">");
        appendSummaryRow(html,
                "Gross sales", summary.getGrossSales(),
                "Discounts", summary.getTotalDiscounts(),
                "Returns/refunds", summary.getTotalReturnsRefunds(),
                "Net sales", summary.getNetSales());
        appendSummaryRow(html,
                "Cash sales", summary.getCashSales(),
                "Card sales", summary.getCardSales(),
                "Wallet sales", summary.getWalletSales(),
                "Credit sales", summary.getCreditSales());
        appendSummaryRow(html,
                "Total expenses", summary.getTotalExpenses(),
                "Opening cash", summary.getOpeningCash(),
                "Expected cash", summary.getExpectedCash(),
                "Actual counted cash", summary.getActualCountedCash());
        appendSummaryRow(html,
                "Cash difference", summary.getCashDifference(),
                "Cash in", summary.getCashIn(),
                "Cash refunds", summary.getCashRefunds(),
                "Cash out", summary.getCashOut());
        html.append("</table>");
    }

    private void appendSummaryRow(StringBuilder html,
                                  String label1, BigDecimal value1,
                                  String label2, BigDecimal value2,
                                  String label3, BigDecimal value3,
                                  String label4, BigDecimal value4) {
        html.append("<tr>");
        appendSummaryCell(html, label1, value1);
        appendSummaryCell(html, label2, value2);
        appendSummaryCell(html, label3, value3);
        appendSummaryCell(html, label4, value4);
        html.append("</tr>");
    }

    private void appendSummaryCell(StringBuilder html, String label, BigDecimal value) {
        html.append("<td><span class=\"label\">").append(escapeHtml(label)).append("</span>")
                .append("<span class=\"value\">").append(formatMoney(value)).append("</span></td>");
    }

    private void appendPaymentBreakdown(StringBuilder html, DailyCashClosingReportData data) {
        html.append("""
                <h2>Payment Method Breakdown</h2>
                <table class="grid">
                  <thead><tr>
                    <th>Payment method</th><th>Invoice count</th><th>Gross amount</th><th>Discount amount</th><th>Return amount</th><th>Net amount</th>
                  </tr></thead><tbody>
                """);
        for (DailyCashClosingPaymentBreakdownRow row : data.getPaymentBreakdown()) {
            html.append("<tr>")
                    .append("<td>").append(escapeHtml(row.getPaymentMethod())).append("</td>")
                    .append("<td class=\"right\">").append(row.getInvoiceCount()).append("</td>")
                    .append("<td class=\"right\">").append(formatMoney(row.getGrossAmount())).append("</td>")
                    .append("<td class=\"right\">").append(formatMoney(row.getDiscountAmount())).append("</td>")
                    .append("<td class=\"right\">").append(formatMoney(row.getReturnAmount())).append("</td>")
                    .append("<td class=\"right\">").append(formatMoney(row.getNetAmount())).append("</td>")
                    .append("</tr>");
        }
        appendEmptyRow(html, data.getPaymentBreakdown().isEmpty(), 6);
        html.append("</tbody></table>");
    }

    private void appendCashMovements(StringBuilder html, DailyCashClosingReportData data) {
        DailyCashClosingSummary summary = data.getSummary();
        html.append("<h2>Cash Movement</h2>");
        html.append("<div class=\"meta\"><b>Expected Cash =</b> Opening Cash + Cash Sales + Cash In - Cash Refunds - Cash Expenses - Cash Out = ")
                .append(formatMoney(summary.getExpectedCash()))
                .append("</div>");
        html.append("""
                <table class="grid">
                  <thead><tr><th>Movement type</th><th>Count</th><th>Amount</th></tr></thead><tbody>
                """);
        for (DailyCashClosingCashMovementRow row : data.getCashMovements()) {
            html.append("<tr>")
                    .append("<td>").append(escapeHtml(row.getMovementType())).append("</td>")
                    .append("<td class=\"right\">").append(row.getMovementCount()).append("</td>")
                    .append("<td class=\"right\">").append(formatMoney(row.getAmount())).append("</td>")
                    .append("</tr>");
        }
        appendEmptyRow(html, data.getCashMovements().isEmpty(), 3);
        html.append("</tbody></table>");
    }

    private void appendInvoices(StringBuilder html, DailyCashClosingReportData data) {
        html.append("""
                <h2>Invoice Summary</h2>
                <table class="grid">
                  <thead><tr>
                    <th>Invoice no</th><th>Date/time</th><th>Cashier</th><th>Customer</th><th>Payment method</th>
                    <th>Gross</th><th>Discount</th><th>Return</th><th>Net</th><th>Status</th>
                  </tr></thead><tbody>
                """);
        data.getInvoices().stream().limit(pdfMaxRows).forEach(row -> html.append("<tr>")
                .append("<td>").append(row.getInvoiceNo()).append("</td>")
                .append("<td>").append(formatDateTime(row.getDateTime())).append("</td>")
                .append("<td>").append(escapeHtml(row.getCashier())).append("</td>")
                .append("<td>").append(escapeHtml(row.getCustomer())).append("</td>")
                .append("<td>").append(escapeHtml(row.getPaymentMethod())).append("</td>")
                .append("<td class=\"right\">").append(formatMoney(row.getGrossAmount())).append("</td>")
                .append("<td class=\"right\">").append(formatMoney(row.getDiscountAmount())).append("</td>")
                .append("<td class=\"right\">").append(formatMoney(row.getReturnAmount())).append("</td>")
                .append("<td class=\"right\">").append(formatMoney(row.getNetAmount())).append("</td>")
                .append("<td>").append(escapeHtml(row.getStatus())).append("</td>")
                .append("</tr>"));
        appendEmptyRow(html, data.getInvoices().isEmpty(), 10);
        html.append("</tbody></table>");
    }

    private void appendExpenses(StringBuilder html, DailyCashClosingReportData data) {
        html.append("""
                <h2>Expenses Summary</h2>
                <table class="grid">
                  <thead><tr>
                    <th>Expense no</th><th>Expense type</th><th>Date/time</th><th>Paid by</th><th>Payment method</th><th>Amount</th><th>Notes</th>
                  </tr></thead><tbody>
                """);
        data.getExpenses().stream().limit(pdfMaxRows).forEach(row -> html.append("<tr>")
                .append("<td>").append(row.getExpenseNo()).append("</td>")
                .append("<td>").append(escapeHtml(row.getExpenseType())).append("</td>")
                .append("<td>").append(formatDateTime(row.getDateTime())).append("</td>")
                .append("<td>").append(escapeHtml(row.getPaidBy())).append("</td>")
                .append("<td>").append(escapeHtml(row.getPaymentMethod())).append("</td>")
                .append("<td class=\"right\">").append(formatMoney(row.getAmount())).append("</td>")
                .append("<td>").append(escapeHtml(row.getNotes())).append("</td>")
                .append("</tr>"));
        appendEmptyRow(html, data.getExpenses().isEmpty(), 7);
        html.append("</tbody></table>");
    }

    private void appendFooter(StringBuilder html, DailyCashClosingReportData data) {
        html.append("<div class=\"meta\"><b>Total invoices:</b> ")
                .append(data.getSummary().getTotalInvoices())
                .append(" &nbsp; <b>Total returns:</b> ")
                .append(data.getSummary().getTotalReturns())
                .append("</div>");
        html.append("""
                <table class="signatures">
                  <tr><td>Prepared by</td><td>Cashier signature</td><td>Manager signature</td></tr>
                </table>
                """);
    }

    private void appendEmptyRow(StringBuilder html, boolean empty, int columns) {
        if (empty) {
            html.append("<tr><td colspan=\"").append(columns).append("\" class=\"muted\">No data found</td></tr>");
        }
    }

    private BigDecimal movementAmount(Map<String, DailyCashClosingCashMovementRow> movements, String type) {
        DailyCashClosingCashMovementRow row = movements.get(type);
        return row == null ? ZERO : scale(row.getAmount());
    }

    private BigDecimal firstNonZero(BigDecimal preferred, BigDecimal fallback) {
        BigDecimal scaledPreferred = scale(preferred);
        return scaledPreferred.compareTo(BigDecimal.ZERO) == 0 ? scale(fallback) : scaledPreferred;
    }

    private String formatMoney(BigDecimal value) {
        return scale(value).toPlainString();
    }

    private BigDecimal scale(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private String formatInstant(Instant value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value.atZone(ZoneId.systemDefault()));
    }

    private String escapeHtml(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
