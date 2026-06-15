package com.example.valueinsoftbackend.Controller;

import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportConfirmResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportBatchSummaryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportFileDownloadResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportHistoryResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportMode;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportRowsPageResponse;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportValidateResponse;
import com.example.valueinsoftbackend.Service.security.AuthorizationService;
import com.example.valueinsoftbackend.Service.product.ProductImportConfirmService;
import com.example.valueinsoftbackend.Service.product.ProductImportErrorReportService;
import com.example.valueinsoftbackend.Service.product.ProductImportTemplateService;
import com.example.valueinsoftbackend.Service.product.ProductImportValidationService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

@RestController
@Validated
@RequestMapping("/api/inventory/products/import")
public class InventoryProductImportController {

    private static final String TEMPLATE_FILE_NAME = "products_import_template.csv";
    private static final String ERROR_REPORT_FILE_NAME = "products_import_errors.csv";

    private final ProductImportTemplateService templateService;
    private final ProductImportValidationService validationService;
    private final ProductImportConfirmService confirmService;
    private final ProductImportErrorReportService errorReportService;
    private final AuthorizationService authorizationService;

    public InventoryProductImportController(ProductImportTemplateService templateService,
                                            ProductImportValidationService validationService,
                                            ProductImportConfirmService confirmService,
                                            ProductImportErrorReportService errorReportService,
                                            AuthorizationService authorizationService) {
        this.templateService = templateService;
        this.validationService = validationService;
        this.confirmService = confirmService;
        this.errorReportService = errorReportService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(Principal principal,
                                                   @RequestParam @Positive Integer companyId,
                                                   @RequestParam @Positive Integer branchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        byte[] content = templateService.buildCsvTemplate().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(TEMPLATE_FILE_NAME).build().toString())
                .body(content);
    }

    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProductImportValidateResponse validateImport(Principal principal,
                                                        @RequestParam @Positive Integer companyId,
                                                        @RequestParam @Positive Integer branchId,
                                                        @RequestParam(defaultValue = "ADD_ONLY") ProductImportMode mode,
                                                        @RequestParam(defaultValue = "false") boolean createMissingCategories,
                                                        @RequestParam(defaultValue = "false") boolean createMissingSuppliers,
                                                        @RequestParam(defaultValue = "false") boolean allowSellingBelowPurchase,
                                                        @RequestParam("file") MultipartFile file) {
        assertImportModeCapability(principal.getName(), companyId, branchId, mode);

        return validationService.validate(
                principal.getName(),
                companyId,
                branchId,
                mode,
                createMissingCategories,
                createMissingSuppliers,
                allowSellingBelowPurchase,
                file);
    }

    @GetMapping("/{batchId}/rows")
    public ProductImportRowsPageResponse previewRows(Principal principal,
                                                     @RequestParam @Positive Integer companyId,
                                                     @RequestParam @Positive Integer branchId,
                                                     @PathVariable @Positive Long batchId,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        return validationService.rows(companyId, batchId, status, page, size);
    }

    @GetMapping("/history")
    public ProductImportHistoryResponse history(Principal principal,
                                                @RequestParam @Positive Integer companyId,
                                                @RequestParam @Positive Integer branchId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        return validationService.history(companyId, branchId, page, size);
    }

    @GetMapping("/{batchId}/summary")
    public ProductImportBatchSummaryResponse batchSummary(Principal principal,
                                                          @RequestParam @Positive Integer companyId,
                                                          @RequestParam @Positive Integer branchId,
                                                          @PathVariable @Positive Long batchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        ProductImportBatchSummaryResponse summary = validationService.summary(companyId, batchId);
        if (!Integer.valueOf(branchId).equals(summary.branchId())) {
            throw new com.example.valueinsoftbackend.ExceptionPack.ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "IMPORT_BATCH_NOT_FOUND",
                    "Import batch was not found");
        }
        return summary;
    }

    @PostMapping("/{batchId}/confirm")
    public ProductImportConfirmResponse confirmImport(Principal principal,
                                                      @RequestParam @Positive Integer companyId,
                                                      @RequestParam @Positive Integer branchId,
                                                      @PathVariable @Positive Long batchId) {
        return confirmService.confirm(principal.getName(), companyId, branchId, batchId);
    }

    @GetMapping("/{batchId}/errors.csv")
    public ResponseEntity<byte[]> downloadErrorReport(Principal principal,
                                                      @RequestParam @Positive Integer companyId,
                                                      @RequestParam @Positive Integer branchId,
                                                      @PathVariable @Positive Long batchId) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        byte[] content = errorReportService.buildErrorReportCsv(companyId, branchId, batchId)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ERROR_REPORT_FILE_NAME).build().toString())
                .body(content);
    }

    @GetMapping("/{batchId}/files/{fileType}/download-url")
    public ProductImportFileDownloadResponse fileDownloadUrl(Principal principal,
                                                             @RequestParam @Positive Integer companyId,
                                                             @RequestParam @Positive Integer branchId,
                                                             @PathVariable @Positive Long batchId,
                                                             @PathVariable String fileType) {
        authorizationService.assertAuthenticatedCapability(
                principal.getName(),
                companyId,
                branchId,
                "inventory.item.read"
        );

        return validationService.fileDownloadUrl(companyId, branchId, batchId, fileType);
    }

    private void assertImportModeCapability(String principalName, int companyId, int branchId, ProductImportMode mode) {
        switch (mode) {
            case ADD_ONLY -> authorizationService.assertAuthenticatedCapability(
                    principalName, companyId, branchId, "inventory.item.create");
            case UPDATE_ONLY -> authorizationService.assertAuthenticatedCapability(
                    principalName, companyId, branchId, "inventory.item.edit");
            case UPSERT -> {
                authorizationService.assertAuthenticatedCapability(
                        principalName, companyId, branchId, "inventory.item.create");
                authorizationService.assertAuthenticatedCapability(
                        principalName, companyId, branchId, "inventory.item.edit");
            }
        }
    }
}
