package com.example.shortlink.dto.response;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

@Getter
public class ShortLinkResponse {

    /**
     * 短链短码。
     */
    private final String shortCode;
    /**
     * 可直接访问的完整短链地址。
     */
    private final String shortUrl;
    /**
     * 短链类型。
     */
    private final LinkType type;
    /**
     * 原始长链接列表，保存为不可变副本。
     */
    private final List<String> originalUrls;
    /**
     * 来源渠道。
     */
    private final String channel;
    /**
     * 短链创建时间。
     */
    private final LocalDateTime createdAt;
    /**
     * 当前累计解析次数。
     */
    private final long resolveCount;
    /**
     * 当前短链状态。
     */
    private final LinkStatus status;
    /**
     * 盲盒剩余有效次数；普通短链为 null。
     */
    private final Integer remainingTimes;
    /**
     * 断链原因。
     */
    private final String brokenReason;
    /**
     * 最近一次可达性检测时间。
     */
    private final LocalDateTime lastCheckedAt;

    public ShortLinkResponse(
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
        this.shortCode = shortCode;
        this.shortUrl = shortUrl;
        this.type = type;
        this.originalUrls = List.copyOf(Objects.requireNonNull(originalUrls, "originalUrls must not be null"));
        this.channel = channel;
        this.createdAt = createdAt;
        this.resolveCount = resolveCount;
        this.status = status;
        this.remainingTimes = remainingTimes;
        this.brokenReason = brokenReason;
        this.lastCheckedAt = lastCheckedAt;
    }

}
