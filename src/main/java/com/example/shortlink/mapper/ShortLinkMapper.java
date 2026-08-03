package com.example.shortlink.mapper;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.ResolveResult;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkMapper {

    private final ShortLinkProperties properties;

    public ShortLinkMapper(ShortLinkProperties properties) {
        this.properties = properties;
    }

    public ShortLinkResponse toResponse(ShortLink shortLink) {
        return new ShortLinkResponse(
                shortLink.getShortCode(),
                buildShortUrl(shortLink.getShortCode()),
                shortLink.getType(),
                shortLink.getOriginalUrls(),
                shortLink.getChannel(),
                shortLink.getCreatedAt(),
                shortLink.getResolveCount().get(),
                shortLink.getStatus(),
                getRemainingTimes(shortLink),
                shortLink.getBrokenReason(),
                shortLink.getLastCheckedAt());
    }

    public ResolveResult toResolveResult(ShortLink shortLink, String targetUrl) {
        return new ResolveResult(
                shortLink.getShortCode(),
                targetUrl,
                shortLink.getType(),
                shortLink.getChannel(),
                shortLink.getCreatedAt(),
                shortLink.getResolveCount().get(),
                getRemainingTimes(shortLink),
                shortLink.getStatus());
    }

    public ResolveResponse toResolveResponse(ResolveResult result) {
        return new ResolveResponse(
                result.shortCode(),
                result.targetUrl(),
                result.type(),
                result.channel(),
                result.createdAt(),
                result.resolveCount(),
                result.remainingTimes(),
                result.status());
    }

    private Integer getRemainingTimes(ShortLink shortLink) {
        return shortLink.getRemainingTimes() == null
                ? null
                : shortLink.getRemainingTimes().get();
    }

    private String buildShortUrl(String shortCode) {
        String domain = properties.getDomain();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain + "/" + shortCode;
    }
}
