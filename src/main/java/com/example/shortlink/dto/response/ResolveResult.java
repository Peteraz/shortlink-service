package com.example.shortlink.dto.response;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ResolveResult {

    /**
     * 被解析的短码。
     */
    private final String shortCode;
    /**
     * 本次解析得到的目标长链接。
     */
    private final String targetUrl;
    /**
     * 短链类型。
     */
    private final LinkType type;
    /**
     * 来源渠道。
     */
    private final String channel;
    /**
     * 短链创建时间。
     */
    private final LocalDateTime createdAt;
    /**
     * 解析成功后的累计访问次数。
     */
    private final long resolveCount;
    /**
     * 解析成功后的盲盒剩余次数；普通短链为 null。
     */
    private final Integer remainingTimes;
    /**
     * 解析完成后的短链状态。
     */
    private final LinkStatus status;

}
