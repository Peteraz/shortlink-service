package com.example.shortlink.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBlindBoxLinkRequest(
        @NotEmpty(message = "originalUrls must not be empty")
        @Size(min = 2, max = 100, message = "originalUrls must contain between 2 and 100 URLs")
        List<@NotBlank(message = "originalUrl must not be blank")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters") String> originalUrls,

        @Size(max = 32, message = "channel must not exceed 32 characters")
        @Pattern(regexp = "^\\s*[\\p{L}\\p{N}_-]*\\s*$", message = "channel may contain only letters, numbers, Chinese characters, underscores, or hyphens")
        String channel,

        @NotNull(message = "validTimes must not be null")
        @Min(value = 1, message = "validTimes must be at least 1")
        @Max(value = 1_000_000, message = "validTimes must be at most 1000000")
        Integer validTimes) {
}
