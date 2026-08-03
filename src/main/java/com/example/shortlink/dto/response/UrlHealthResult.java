package com.example.shortlink.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UrlHealthResult {

    /**
     * 被检测的原始 URL。
     */
    private final String url;
    /**
     * 该 URL 是否可达。
     */
    private final boolean reachable;
    /**
     * 目标返回的 HTTP 状态码。
     */
    private final Integer httpStatus;
    /**
     * 该 URL 的检测消息。
     */
    private final String message;
    /**
     * 本次请求耗时，单位为毫秒。
     */
    private final long elapsedMillis;

}
