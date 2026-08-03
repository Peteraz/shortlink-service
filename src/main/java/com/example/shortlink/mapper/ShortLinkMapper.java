package com.example.shortlink.mapper;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.ShortLinkResponse;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkMapper {

    private final ShortLinkProperties properties;

    public ShortLinkMapper(ShortLinkProperties properties) {
        this.properties = properties;
    }

    public ShortLinkResponse toResponse(ShortLink shortLink) {
        Integer remainingTimes = shortLink.getRemainingTimes() == null
                ? null
                : shortLink.getRemainingTimes().get();
        return new ShortLinkResponse(
                shortLink.getShortCode(),
                buildShortUrl(shortLink.getShortCode()),
                shortLink.getType(),
                shortLink.getOriginalUrls(),
                shortLink.getChannel(),
                shortLink.getCreatedAt(),
                shortLink.getResolveCount().get(),
                shortLink.getStatus(),
                remainingTimes,
                shortLink.getBrokenReason(),
                shortLink.getLastCheckedAt());
    }

    private String buildShortUrl(String shortCode) {
        String domain = properties.getDomain();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain + "/" + shortCode;
    }
}
