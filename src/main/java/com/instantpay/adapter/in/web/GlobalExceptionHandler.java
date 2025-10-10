package com.instantpay.adapter.in.web;

import com.instantpay.application.dto.ApiError;
import com.instantpay.domain.error.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String TYPE_VALIDATION = "about:blank/validation-error";
    private static final String TYPE_CONFLICT   = "about:blank/conflict";
    private static final String TYPE_NOT_FOUND  = "about:blank/not-found";
    private static final String TYPE_BUSINESS   = "about:blank/business-rule";
    private static final String TYPE_INTERNAL   = "about:blank/internal-error";
    private static final String TYPE_BAD_REQ    = "about:blank/bad-request";

    // ---- Validation: @Valid body (Bean Validation) ----
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.put(fe.getField(), fe.getDefaultMessage());
        }
        return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Validation failed",
                "Request body contains invalid fields.", req, Map.of("fields", fields));
    }

    // ---- Validation: @Validated path/query params ----
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return problem(HttpStatus.BAD_REQUEST, TYPE_VALIDATION, "Constraint violation",
                "Request parameters are invalid.", req, Map.of("violations", errors));
    }

    // ---- JSON parse errors ----
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQ, "Malformed JSON",
                "Request body could not be parsed.", req, null);
    }

    // ---- Type mismatch in path/query ----
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        Map<String, Object> add = Map.of(
                "parameter", ex.getName(),
                "requiredType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
                "value", String.valueOf(ex.getValue())
        );
        return problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQ, "Parameter type mismatch",
                "A request parameter has the wrong type.", req, add);
    }

    // ---- Missing headers (e.g., Idempotency-Key if you require it) ----
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return problem(HttpStatus.BAD_REQUEST, TYPE_BAD_REQ, "Missing header",
                ex.getMessage(), req, Map.of("header", ex.getHeaderName()));
    }

    // ---- Domain mapping ----
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(AccountNotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, TYPE_NOT_FOUND, "Account not found", ex.getMessage(), req, null);
    }

    @ExceptionHandler({DuplicatePaymentException.class, IdempotencyConflictException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest req) {
        return problem(HttpStatus.CONFLICT, TYPE_CONFLICT, "Conflict", ex.getMessage(), req, null);
    }

    @ExceptionHandler({InsufficientFundsException.class, PaymentRejectedException.class})
    public ResponseEntity<ApiError> handleBusiness(RuntimeException ex, HttpServletRequest req) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, TYPE_BUSINESS, "Business rule violation", ex.getMessage(), req, null);
    }

    // ---- Spring's ErrorResponseException (e.g., thrown by WebFlux/Spring 6 APIs) ----
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ApiError> handleErrorResponse(ErrorResponseException ex, HttpServletRequest req) {
        HttpStatus status = (HttpStatus) ex.getStatusCode();
        return problem(status, TYPE_BAD_REQ, status.getReasonPhrase(), ex.getMessage(), req, null);
    }

    // ---- Fallback ----
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex, HttpServletRequest req) {
        // Log full stack internally; keep message generic for clients
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, TYPE_INTERNAL, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", req, null);
    }

    // ---- Helper ----
    private ResponseEntity<ApiError> problem(HttpStatus status, String type, String title,
                                             String detail, HttpServletRequest req,
                                             Map<String, Object> extra) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        Map<String, Object> bag = (extra != null) ? new HashMap<>(extra) : null;
        String traceId = firstNonBlank(req.getHeader("X-Request-Id"), MDC.get("traceId"));

        var body = new ApiError(
                type,
                title,
                status.value(),
                detail,
                req.getRequestURI(),
                traceId,
                Instant.now(),
                bag
        );
        return new ResponseEntity<>(body, headers, status);
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
