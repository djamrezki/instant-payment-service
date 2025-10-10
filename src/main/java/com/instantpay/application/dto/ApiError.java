package com.instantpay.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String type,          // URI or constant string for error category
        String title,         // short, human title
        int status,           // HTTP status code
        String detail,        // human detail (safe for clients)
        String instance,      // the request path
        String traceId,       // correlation id (from header or MDC)
        Instant timestamp,    // server time
        Map<String, Object> additional // optional bag for field errors etc.
) {}
