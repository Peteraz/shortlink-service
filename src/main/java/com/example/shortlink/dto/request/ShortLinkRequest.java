package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShortLinkRequest {
    /**
     * 短链。
     */
    @NotBlank(message = "短链不能为空")
    private String shortUrl;

    private boolean markBroken;
}
