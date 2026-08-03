package com.example.shortlink.dto.response;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResponse {
        content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
    }
}
