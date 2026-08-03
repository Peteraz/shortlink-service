package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkBrokenRequest(
        @NotBlank(message = "reason must not be blank")
        @Size(max = 200, message = "reason must not exceed 200 characters")
        String reason) {

    public MarkBrokenRequest {
        if (reason != null) {
            reason = reason.trim();
        }
    }
}
