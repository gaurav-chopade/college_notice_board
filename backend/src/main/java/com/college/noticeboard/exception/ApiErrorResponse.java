package com.college.noticeboard.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(boolean success, String message, LocalDateTime timestamp, Map<String, String> errors) {

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(false, message, LocalDateTime.now(), null);
    }

    public static ApiErrorResponse of(String message, Map<String, String> errors) {
        return new ApiErrorResponse(false, message, LocalDateTime.now(), errors);
    }
}
