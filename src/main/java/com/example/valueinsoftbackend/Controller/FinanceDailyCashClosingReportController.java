package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.Request.Finance.DailyCashClosingReportRequest;
import com.example.valueinsoftbackend.Service.finance.FinanceDailyCashClosingReportService;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@Validated
@RequestMapping("/api/reports/finance")
public class FinanceDailyCashClosingReportController {

    private static final String FILE_NAME = "daily-sales-cash-closing-report.pdf";

    private final FinanceDailyCashClosingReportService reportService;

    public FinanceDailyCashClosingReportController(FinanceDailyCashClosingReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily-cash-closing/pdf")
    public ResponseEntity<StreamingResponseBody> exportPdf(Principal principal,
                                                           @RequestParam @Positive int companyId,
                                                           @RequestParam @Positive int branchId,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                                                           @RequestParam(required = false) String cashierId,
                                                           @RequestParam(required = false) Integer shiftId,
                                                           @RequestParam(required = false) String paymentMethod,
                                                           @RequestParam(required = false) String status) {
        DailyCashClosingReportRequest request = new DailyCashClosingReportRequest(
                companyId,
                branchId,
                dateFrom,
                dateTo,
                cashierId,
                shiftId,
                paymentMethod,
                status);
        StreamingResponseBody body = outputStream -> reportService.writePdf(principal.getName(), request, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(FILE_NAME).build().toString())
                .body(body);
    }
}
