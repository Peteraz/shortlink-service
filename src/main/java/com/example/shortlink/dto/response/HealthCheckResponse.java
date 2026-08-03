package com.example.shortlink.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

@Getter
public class HealthCheckResponse {

    /**
     * 被检测的短码。
     */
    private final String shortCode;
    /**
     * 短链整体是否可达。
     */
    private final boolean reachable;
    /**
     * 整体结果代表性的 HTTP 状态码。
     */
    private final Integer httpStatus;
    /**
     * 整体检测消息。
     */
    private final String message;
    /**
     * 本次检测完成时间。
     */
    private final LocalDateTime checkedAt;
    /**
     * 本次检测是否自动标记了断链。
     */
    private final boolean markedBroken;
    /**
     * 每个原始 URL 的检测结果。
     */
    private final List<UrlHealthResult> urlResults;

    public HealthCheckResponse(
            String shortCode,
            boolean reachable,
            Integer httpStatus,
            String message,
            LocalDateTime checkedAt,
            boolean markedBroken,
            List<UrlHealthResult> urlResults) {
        this.shortCode = shortCode;
        this.reachable = reachable;
        this.httpStatus = httpStatus;
        this.message = message;
        this.checkedAt = checkedAt;
        this.markedBroken = markedBroken;
        this.urlResults = List.copyOf(Objects.requireNonNull(urlResults, "urlResults must not be null"));
    }

}
