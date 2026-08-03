package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNormalLinkRequest {

    /** 原始长链接。 */
    @NotBlank(message = "originalUrl must not be blank")
    @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
    private String originalUrl;

    /** 来源渠道；为空时使用 default。 */
    @Size(max = 32, message = "channel must not exceed 32 characters")
    @Pattern(regexp = "^\\s*[\\p{L}\\p{N}_-]*\\s*$", message = "channel may contain only letters, numbers, Chinese characters, underscores, or hyphens")
    private String channel;

}
