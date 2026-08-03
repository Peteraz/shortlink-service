package com.example.shortlink.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchHealthCheckRequest(
        @NotEmpty(message = "shortCodes must not be empty")
        @Size(max = 100, message = "shortCodes must not contain more than 100 items")
        List<@NotBlank(message = "shortCode must not be blank") String> shortCodes,
        boolean markBroken) {
}
