package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MarkBrokenRequest {

    /**
     * 主动断链原因，保存前会去除首尾空格。
     */
    @NotBlank(message = "reason must not be blank")
    @Size(max = 200, message = "reason must not exceed 200 characters")
    private String reason;

    public MarkBrokenRequest(String reason) {
        this.reason = normalize(reason);
    }

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
