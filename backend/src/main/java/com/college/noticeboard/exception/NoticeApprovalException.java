package com.college.noticeboard.exception;

import org.springframework.http.HttpStatus;

public class NoticeApprovalException extends ApiException {

    public NoticeApprovalException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public NoticeApprovalException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
