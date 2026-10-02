package com.college.noticeboard;

import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.service.FileValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidatorTest {

    private static final long MAX = 10 * 1024 * 1024;

    @Test
    void acceptsRealPdf() {
        var f = new MockMultipartFile("files", "../../etc/Time table.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        var v = FileValidator.validate(f, MAX);
        assertThat(v.extension()).isEqualTo("pdf");
        assertThat(v.image()).isFalse();
        assertThat(v.safeOriginalName()).isEqualTo("Time table.pdf");
    }

    @Test
    void acceptsPng() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertThat(FileValidator.validate(new MockMultipartFile("files", "a.png", "image/png", png), MAX).image()).isTrue();
    }

    @Test
    void rejectsRenamedExecutable() {
        var f = new MockMultipartFile("files", "virus.pdf", "application/pdf", "MZ\u0090\u0000binary".getBytes());
        assertThatThrownBy(() -> FileValidator.validate(f, MAX)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsUnsupportedExtensionAndMismatchedMime() {
        assertThatThrownBy(() -> FileValidator.validate(
                new MockMultipartFile("files", "a.exe", "application/octet-stream", "MZ".getBytes()), MAX))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> FileValidator.validate(
                new MockMultipartFile("files", "a.pdf", "image/png", "%PDF-1.4".getBytes()), MAX))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsOversizedFile() {
        var f = new MockMultipartFile("files", "a.pdf", "application/pdf", "%PDF-1.4 more bytes".getBytes());
        assertThatThrownBy(() -> FileValidator.validate(f, 5)).isInstanceOf(BadRequestException.class);
    }
}
