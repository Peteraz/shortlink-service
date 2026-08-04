package com.example.shortlink.mapper;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.ResolveResult;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import org.springframework.stereotype.Component;

@Component
public class ShortLinkMapper {

    /**
     * 用于拼接完整短链地址的配置。
     */
    private final ShortLinkProperties properties;

    public ShortLinkMapper(ShortLinkProperties properties) {
        this.properties = properties;
    }

    /**
     * 将领域对象快照转换为响应 DTO，并读取原子计数的普通数值。
     */
    public ShortLinkResponse toResponse(ShortLink shortLink) {
        return shortLink.withStateLock(() -> new ShortLinkResponse(
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
                shortLink.getLastCheckedAt()));
    }

    public ResolveResult toResolveResult(ShortLink shortLink, String targetUrl) {
        return shortLink.withStateLock(() -> new ResolveResult(
                shortLink.getShortCode(),
                targetUrl,
                shortLink.getType(),
                shortLink.getChannel(),
                shortLink.getCreatedAt(),
                shortLink.getResolveCount().get(),
                getRemainingTimes(shortLink),
                shortLink.getStatus()));
    }

    /**
     * 将已经完成的解析结果转换为 HTTP API 使用的 DTO。
     */
    public ResolveResponse toResolveResponse(ResolveResult result) {
        return new ResolveResponse(
                result.getShortCode(),
                result.getTargetUrl(),
                result.getType(),
                result.getChannel(),
                result.getCreatedAt(),
                result.getResolveCount(),
                result.getRemainingTimes(),
                result.getStatus());
    }

    private Integer getRemainingTimes(ShortLink shortLink) {
        return shortLink.getRemainingTimes() == null
                ? null
                : shortLink.getRemainingTimes().get();
    }


    /**
     * 去除域名末尾的斜杠后，拼接公网跳转路径和短码，避免生成双斜杠。
     *
     * <p>例如：{@code http://localhost:8090/} 和 {@code Ab12xY7}
     * 拼接后得到 {@code http://localhost:8090/s/Ab12xY7}。</p>
     */
    private String buildShortUrl(String shortCode) {
        String domain = properties.getDomain();
        while (domain.endsWith("/")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain + "/s/" + shortCode;
    }
}
