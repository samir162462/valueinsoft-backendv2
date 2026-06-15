package com.example.valueinsoftbackend.Service;

import com.example.valueinsoftbackend.DatabaseRequests.InventoryImport.ProductImportRepository;
import com.example.valueinsoftbackend.ExceptionPack.ApiException;
import com.example.valueinsoftbackend.Model.InventoryImport.ProductImportFileDownloadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class ProductImportFileStorageService {

    private static final long DOWNLOAD_EXPIRY_SECONDS = 3600L;

    private final StorageService storageService;
    private final ProductImportRepository importRepository;

    public ProductImportFileStorageService(StorageService storageService,
                                           ProductImportRepository importRepository) {
        this.storageService = storageService;
        this.importRepository = importRepository;
    }

    public void storeOriginalCsv(int companyId, int branchId, long batchId, MultipartFile file) {
        String key = baseKey(companyId, branchId, batchId) + "/original/" + safeFileName(file.getOriginalFilename(), "products_import.csv");
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "text/csv"
                : file.getContentType();
        try {
            storageService.uploadFile(key, file.getBytes(), contentType);
            importRepository.updateOriginalFileStorage(companyId, batchId, key, file.getSize(), contentType);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMPORT_FILE_STORAGE_FAILED", "Unable to save uploaded CSV file");
        }
    }

    public void storeErrorReportCsv(int companyId, int branchId, long batchId, String csvContent) {
        String key = baseKey(companyId, branchId, batchId) + "/reports/products_import_errors.csv";
        byte[] content = csvContent.getBytes(StandardCharsets.UTF_8);
        storageService.uploadFile(key, content, "text/csv; charset=UTF-8");
        importRepository.updateErrorReportStorage(companyId, batchId, key, content.length);
    }

    public ProductImportFileDownloadResponse downloadUrl(int companyId, int branchId, long batchId, String fileType) {
        String normalizedType = normalizeFileType(fileType);
        String key = importRepository.findBatchFileKey(companyId, branchId, batchId, normalizedType);
        if (key == null || key.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "IMPORT_FILE_NOT_FOUND", "Import file is not available");
        }

        String fileName = "ERROR_REPORT".equals(normalizedType)
                ? "products_import_errors.csv"
                : fileNameFromKey(key, "products_import.csv");
        return new ProductImportFileDownloadResponse(
                batchId,
                normalizedType,
                fileName,
                storageService.generatePresignedDownloadUrl(key).toString(),
                DOWNLOAD_EXPIRY_SECONDS);
    }

    private String baseKey(int companyId, int branchId, long batchId) {
        return "inventory/product-import/company-" + companyId
                + "/branch-" + branchId
                + "/" + LocalDate.now()
                + "/batch-" + batchId;
    }

    private String normalizeFileType(String fileType) {
        String value = fileType == null ? "" : fileType.trim().toUpperCase(Locale.ROOT);
        if (!"ORIGINAL".equals(value) && !"ERROR_REPORT".equals(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMPORT_FILE_TYPE_INVALID", "Unsupported import file type");
        }
        return value;
    }

    private String safeFileName(String value, String fallback) {
        String fileName = value == null || value.isBlank() ? fallback : value.trim();
        fileName = fileName.replace("\\", "/");
        int slashIndex = fileName.lastIndexOf('/');
        if (slashIndex >= 0) {
            fileName = fileName.substring(slashIndex + 1);
        }
        fileName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return fileName.isBlank() ? fallback : fileName;
    }

    private String fileNameFromKey(String key, String fallback) {
        int index = key.lastIndexOf('/');
        if (index < 0 || index + 1 >= key.length()) {
            return fallback;
        }
        return key.substring(index + 1);
    }
}
