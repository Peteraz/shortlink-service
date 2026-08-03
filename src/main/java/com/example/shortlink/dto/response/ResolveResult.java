package com.example.shortlink.dto.response;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;

import java.time.LocalDateTime;

public record ResolveResult(
        String shortCode,
        String targetUrl,
        LinkType type,
        String channel,
        LocalDateTime createdAt,
        long resolveCount,
        Integer remainingTimes,
        LinkStatus status) {
}
