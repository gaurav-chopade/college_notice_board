package com.college.noticeboard.exception;

import org.springframework.http.HttpStatus;

public class CloudinaryUploadException extends ApiException {

    public CloudinaryUploadException(String message) {
        super(HttpStatus.BAD_GATEWAY, message);
    }

    public CloudinaryUploadException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, message, cause);
    }
}
