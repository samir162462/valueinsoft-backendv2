package com.example.valueinsoftbackend.Model.InventoryImport;

public record ProductImportFileDownloadResponse(
        Long batchId,
        String fileType,
        String fileName,
        String downloadUrl,
        long expiresInSeconds
) {
}
