package com.example.valueinsoftbackend.Service;

import java.net.URL;

public interface StorageService {
    /**
     * Generates a presigned URL for uploading a file to storage.
     *
     * @param key         The destination key (path) in the storage bucket.
     * @param contentType The MIME type of the file to be uploaded.
     * @return A temporary URL that can be used for a PUT request.
     */
    URL generatePresignedUploadUrl(String key, String contentType);

    /**
     * Generates a presigned URL for downloading a file from storage.
     *
     * @param key The key (path) of the file in the storage bucket.
     * @return A temporary URL for GET access.
     */
    URL generatePresignedDownloadUrl(String key);

    /**
     * Deletes a file from storage.
     *
     * @param key The key (path) of the file to delete.
     */
    void deleteFile(String key);
}
