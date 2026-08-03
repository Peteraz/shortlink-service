package com.example.shortlink.dto.response;

public record UrlHealthResult(
        String url,
        boolean reachable,
        Integer httpStatus,
        String message,
        long elapsedMillis) {
}
