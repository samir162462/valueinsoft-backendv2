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
     * Uploads a file directly to storage.
     *
     * @param key         The destination key (path).
     * @param content     The byte content of the file.
     * @param contentType The MIME type.
     */
    void uploadFile(String key, byte[] content, String contentType);

    /**
     * Deletes a file from storage.
     *
     * @param key The key (path) of the file to delete.
     */
    void deleteFile(String key);
}
