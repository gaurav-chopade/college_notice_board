package com.college.noticeboard.service;

import com.college.noticeboard.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates uploads by extension, declared MIME type, size AND file signature (magic bytes),
 * so a renamed executable cannot pass as a PDF. The original filename is never trusted for storage.
 */
public final class FileValidator {

    private FileValidator() {
    }

    public static final Map<String, Set<String>> ALLOWED = Map.of(
            "pdf", Set.of("application/pdf"),
            "doc", Set.of("application/msword"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "jpg", Set.of("image/jpeg"),
            "jpeg", Set.of("image/jpeg"),
            "png", Set.of("image/png"));

    public record ValidatedFile(String extension, String mimeType, boolean image, String safeOriginalName, long size) {
    }

    public static ValidatedFile validate(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        if (file.getSize() > maxBytes) {
            throw new BadRequestException("File is too large. Maximum size is " + (maxBytes / (1024 * 1024)) + " MB");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String safeName = sanitizeName(original);
        int dot = safeName.lastIndexOf('.');
        if (dot < 0 || dot == safeName.length() - 1) {
            throw new BadRequestException("File must have an extension (pdf, doc, docx, jpg, jpeg, png)");
        }
        String ext = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        Set<String> mimes = ALLOWED.get(ext);
        if (mimes == null) {
            throw new BadRequestException("Unsupported file type ." + ext + ". Allowed: pdf, doc, docx, jpg, jpeg, png");
        }
        String mime = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!mimes.contains(mime)) {
            throw new BadRequestException("File content type " + mime + " does not match ." + ext);
        }
        byte[] head = readHead(file);
        if (!signatureMatches(ext, head)) {
            throw new BadRequestException("File content does not look like a valid ." + ext + " file");
        }
        boolean image = ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png");
        return new ValidatedFile(ext, mime, image, safeName, file.getSize());
    }

    static String sanitizeName(String name) {
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._ ()-]", "_").trim();
        if (base.length() > 200) {
            base = base.substring(base.length() - 200);
        }
        return base.isEmpty() ? "file" : base;
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(8);
        } catch (IOException e) {
            throw new BadRequestException("Could not read uploaded file");
        }
    }

    static boolean signatureMatches(String ext, byte[] h) {
        return switch (ext) {
            case "pdf" -> startsWith(h, 0x25, 0x50, 0x44, 0x46);                  // %PDF
            case "png" -> startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg", "jpeg" -> startsWith(h, 0xFF, 0xD8, 0xFF);
            case "doc" -> startsWith(h, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1); // OLE2
            case "docx" -> startsWith(h, 0x50, 0x4B, 0x03, 0x04);                // ZIP
            default -> false;
        };
    }

    private static boolean startsWith(byte[] data, int... sig) {
        if (data.length < sig.length) {
            return false;
        }
        byte[] expected = new byte[sig.length];
        for (int i = 0; i < sig.length; i++) {
            expected[i] = (byte) sig[i];
        }
        return Arrays.equals(Arrays.copyOf(data, sig.length), expected);
    }
}
