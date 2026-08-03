package com.example.shortlink.controller;

import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/short-links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @PostMapping
    public ApiResponse<ShortLinkResponse> createNormalLink(
            @Valid @RequestBody CreateNormalLinkRequest request) {
        return ApiResponse.success(shortLinkService.createNormalLink(request));
    }

    @GetMapping("/{shortCode}")
    public ApiResponse<ShortLinkResponse> getByShortCode(@PathVariable String shortCode) {
        return ApiResponse.success(shortLinkService.getByShortCode(shortCode));
    }
}
