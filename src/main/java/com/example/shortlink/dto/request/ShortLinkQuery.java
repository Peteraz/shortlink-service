package com.example.shortlink.dto.request;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;

public record ShortLinkQuery(
        String shortCode,
        String channel,
        LinkStatus status,
        LinkType type,
        int page,
        int size) {
}
