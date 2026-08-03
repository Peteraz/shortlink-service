package com.example.shortlink.controller;

import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.service.LinkHealthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/short-links")
public class LinkHealthController {

    private final LinkHealthService linkHealthService;

    public LinkHealthController(LinkHealthService linkHealthService) {
        this.linkHealthService = linkHealthService;
    }

    @PostMapping("/{shortCode}/health-check")
    public ApiResponse<HealthCheckResponse> healthCheck(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "false") boolean markBroken) {
        return ApiResponse.success(linkHealthService.healthCheck(shortCode, markBroken));
    }

    @PostMapping("/batch-health-check")
    public ApiResponse<List<HealthCheckResponse>> batchHealthCheck(
            @Valid @RequestBody BatchHealthCheckRequest request) {
        return ApiResponse.success(linkHealthService.batchHealthCheck(request));
    }
}
