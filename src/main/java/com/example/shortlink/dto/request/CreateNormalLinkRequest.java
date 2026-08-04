package com.example.shortlink.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNormalLinkRequest {

    /**
     * 原始长链接。
    */
    private String originalUrl;

    /**
     * 来源渠道；为空时使用 default。
     */
    private String channel;

}
