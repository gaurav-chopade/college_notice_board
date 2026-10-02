package com.college.noticeboard.service;

/** Abstraction over Cloudinary so the rest of the app (and tests) do not depend on the SDK. */
public interface CloudStorageService {

    record UploadResult(String publicId, String secureUrl, String resourceType, String format, long bytes) {
    }

    boolean isConfigured();

    /**
     * @param folder   e.g. college-notice-board/notices/42
     * @param publicId file name inside the folder (raw files keep their extension)
     * @param image    true → resource_type image, false → raw (PDF/DOC/DOCX)
     */
    UploadResult upload(byte[] data, String folder, String publicId, boolean image);

    /** Deletes the asset. Treats "not found" as success so retries are safe. */
    void delete(String publicId, String resourceType);

    /** Short-lived signed download URL for an authenticated asset. */
    String signedDownloadUrl(String publicId, String resourceType, String format, long expiresAtEpochSeconds);
}
