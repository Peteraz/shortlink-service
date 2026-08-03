package com.example.shortlink.dto.response;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ShortLinkResponse(
        String shortCode,
        String shortUrl,
        LinkType type,
        List<String> originalUrls,
        String channel,
        LocalDateTime createdAt,
        long resolveCount,
        LinkStatus status,
        Integer remainingTimes,
        String brokenReason,
        LocalDateTime lastCheckedAt) {

    public ShortLinkResponse {
        originalUrls = List.copyOf(Objects.requireNonNull(originalUrls, "originalUrls must not be null"));
    }
}
