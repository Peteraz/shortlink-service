package com.example.shortlink.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MarkBrokenRequest {

    /**
     * 主动断链原因，保存前会去除首尾空格。
     */
    private String reason;

    /**
     * 设置断链原因并统一去除首尾空格。
     */
    public void setReason(String reason) {
        this.reason = normalize(reason);
    }

    private static String normalize(String reason) {
        return reason == null ? null : reason.trim();
    }
}
