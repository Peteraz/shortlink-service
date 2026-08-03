package com.example.shortlink.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record HealthCheckResponse(
        String shortCode,
        boolean reachable,
        Integer httpStatus,
        String message,
        LocalDateTime checkedAt,
        boolean markedBroken,
        List<UrlHealthResult> urlResults) {

    public HealthCheckResponse {
        urlResults = List.copyOf(Objects.requireNonNull(urlResults, "urlResults must not be null"));
    }
}
