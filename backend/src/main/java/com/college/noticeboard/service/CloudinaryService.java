package com.college.noticeboard.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.college.noticeboard.exception.CloudinaryUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Cloudinary implementation. Files are uploaded with delivery type "authenticated", so the stored
 * secure_url is NOT publicly downloadable: users get a short-lived signed link from the backend
 * only after the notice-visibility check passes.
 */
@Slf4j
@Service
public class CloudinaryService implements CloudStorageService {

    private static final String DELIVERY_TYPE = "authenticated";

    private final Cloudinary cloudinary;

    public CloudinaryService(@Value("${app.cloudinary.cloud-name:}") String cloudName,
                             @Value("${app.cloudinary.api-key:}") String apiKey,
                             @Value("${app.cloudinary.api-secret:}") String apiSecret) {
        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("Cloudinary is not configured (CLOUDINARY_CLOUD_NAME / API_KEY / API_SECRET). Attachments are disabled.");
            this.cloudinary = null;
        } else {
            this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName, "api_key", apiKey, "api_secret", apiSecret, "secure", true));
        }
    }

    @Override
    public boolean isConfigured() {
        return cloudinary != null;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public UploadResult upload(byte[] data, String folder, String publicId, boolean image) {
        requireConfigured();
        String resourceType = image ? "image" : "raw";
        try {
            Map result = cloudinary.uploader().upload(data, ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "resource_type", resourceType,
                    "type", DELIVERY_TYPE,
                    "overwrite", false,
                    "unique_filename", false,
                    "use_filename", false));
            String id = (String) result.get("public_id");
            String url = (String) result.get("secure_url");
            Object format = result.get("format");
            Object bytes = result.get("bytes");
            if (id == null || url == null) {
                throw new CloudinaryUploadException("Cloudinary did not return a file reference");
            }
            return new UploadResult(id, url, resourceType, format == null ? null : format.toString(),
                    bytes instanceof Number n ? n.longValue() : data.length);
        } catch (CloudinaryUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary upload failed", e);
            throw new CloudinaryUploadException("Could not upload the file to cloud storage. Try again later", e);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void delete(String publicId, String resourceType) {
        requireConfigured();
        try {
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "resource_type", resourceType, "type", DELIVERY_TYPE, "invalidate", true));
            Object status = result.get("result");
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new CloudinaryUploadException("Cloud storage refused to delete the file (" + status + ")");
            }
        } catch (CloudinaryUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary delete failed for {}", publicId, e);
            throw new CloudinaryUploadException("Could not delete the file from cloud storage. Try again later", e);
        }
    }

    @Override
    public String signedDownloadUrl(String publicId, String resourceType, String format, long expiresAtEpochSeconds) {
        requireConfigured();
        try {
            // Raw public IDs already include their extension, so no format is passed for them.
            String fmt = "raw".equals(resourceType) ? "" : format;
            return cloudinary.privateDownload(publicId, fmt, ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "type", DELIVERY_TYPE,
                    "expires_at", expiresAtEpochSeconds,
                    "attachment", true));
        } catch (Exception e) {
            log.error("Could not sign download URL for {}", publicId, e);
            throw new CloudinaryUploadException("Could not prepare the download link. Try again later", e);
        }
    }

    private void requireConfigured() {
        if (cloudinary == null) {
            throw new CloudinaryUploadException("File storage is not configured. Set the CLOUDINARY_* environment variables");
        }
    }
}
