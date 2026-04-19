package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.InventoryAudit.InventoryAuditPageResponse;
import com.example.valueinsoftbackend.Model.Request.InventoryAudit.InventoryAuditSearchRequest;
import com.example.valueinsoftbackend.Service.InventoryAuditService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.validation.Valid;
import java.security.Principal;
import java.time.format.DateTimeFormatter;

@RestController
@Validated
@RequestMapping("/api/inventory/audit")
public class InventoryAuditController {

    private final InventoryAuditService inventoryAuditService;

    public InventoryAuditController(InventoryAuditService inventoryAuditService) {
        this.inventoryAuditService = inventoryAuditService;
    }

    @PostMapping("/search")
    public InventoryAuditPageResponse search(Principal principal,
                                             @Valid @RequestBody InventoryAuditSearchRequest request) {
        return inventoryAuditService.search(principal.getName(), request);
    }

    @PostMapping("/export/excel")
    public ResponseEntity<StreamingResponseBody> exportExcel(Principal principal,
                                                             @Valid @RequestBody InventoryAuditSearchRequest request) {
        String fileName = buildFileName("inventory-audit", request, "xlsx");
        StreamingResponseBody body = outputStream -> inventoryAuditService.writeExcel(principal.getName(), request, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    @PostMapping("/export/pdf")
    public ResponseEntity<StreamingResponseBody> exportPdf(Principal principal,
                                                           @Valid @RequestBody InventoryAuditSearchRequest request) {
        String fileName = buildFileName("inventory-audit", request, "pdf");
        StreamingResponseBody body = outputStream -> inventoryAuditService.writePdf(principal.getName(), request, outputStream);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    private String buildFileName(String prefix, InventoryAuditSearchRequest request, String extension) {
        String from = request.getFromDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String to = request.getToDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-branch-" + request.getBranchId() + "-" + from + "-" + to + "." + extension;
    }
}
