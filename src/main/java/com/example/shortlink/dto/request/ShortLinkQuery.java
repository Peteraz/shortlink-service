package com.example.shortlink.dto.request;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShortLinkQuery {

    /**
     * 精确匹配的短码。
     */
    private final String shortCode;
    /**
     * 精确匹配的渠道。
     */
    private final String channel;
    /**
     * 精确匹配的短链状态。
     */
    private final LinkStatus status;
    /**
     * 精确匹配的短链类型。
     */
    private final LinkType type;
    /**
     * 从 0 开始的页码。
     */
    private final int page;
    /**
     * 每页返回数量。
     */
    private final int size;

}
