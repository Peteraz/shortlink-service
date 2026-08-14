package com.example.shortlink.controller;

import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.request.ShortLinkRequest;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.service.LinkHealthService;
import com.example.shortlink.validator.ShortUrlParser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/short-links")
public class LinkHealthController {

    /**
     * 短链可达性检测服务。
     */
    private final LinkHealthService linkHealthService;
    private final ShortUrlParser shortUrlParser;

    public LinkHealthController(LinkHealthService linkHealthService, ShortUrlParser shortUrlParser) {
        this.linkHealthService = linkHealthService;
        this.shortUrlParser = shortUrlParser;
    }

    /**
     * 检测单个完整短链是否可达；markBroken 为 true 时允许自动标记断链。
     */
    @PostMapping("/health-check")
    public ApiResponse<HealthCheckResponse> healthCheck(@Valid @RequestBody ShortLinkRequest request) {
        String shortCode = shortUrlParser.extractShortCode(request.getShortUrl());
        return ApiResponse.success(linkHealthService.healthCheck(shortCode, request.isMarkBroken()));
    }

    /**
     * 批量检测完整短链是否可达；每条短链独立返回检测结果。
     */
    @PostMapping("/batch-health-check")
    public ApiResponse<List<HealthCheckResponse>> batchHealthCheck(@Valid @RequestBody BatchHealthCheckRequest request) {
        List<String> shortCodes = request.getShortUrls().stream().map(shortUrlParser::extractShortCode).toList();
        return ApiResponse.success(linkHealthService.batchHealthCheck(shortCodes, request.isMarkBroken()));
    }
}
