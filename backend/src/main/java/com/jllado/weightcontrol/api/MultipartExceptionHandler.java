package com.jllado.weightcontrol.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class MultipartExceptionHandler {
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Map<String, Object>> malformedMultipart(HttpServletRequest request) {
        return error(request, HttpStatus.BAD_REQUEST, "Malformed multipart request");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> uploadTooLarge(HttpServletRequest request) {
        return error(request, HttpStatus.PAYLOAD_TOO_LARGE, "Multipart upload exceeds the configured limit");
    }

    private ResponseEntity<Map<String, Object>> error(HttpServletRequest request, HttpStatus status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(Map.of(
            "timestamp", Instant.now(), "status", status.value(), "error", status.getReasonPhrase(),
            "message", message, "path", request.getRequestURI()
        ));
    }
}
