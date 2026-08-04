package com.example.shortlink.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBlindBoxLinkRequest {

    /**
     * 盲盒候选原始长链接列表。
     */
    private List<String> originalUrls;

    /**
     * 来源渠道；为空时使用 default。
     */
    private String channel;

    /**
     * 盲盒允许成功解析的总次数。
     */
    private Integer validTimes;

}
