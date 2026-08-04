package com.example.shortlink.controller;

import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.service.LinkHealthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/short-links")
public class LinkHealthController {

    /**
     * 短链可达性检测服务。
     */
    private final LinkHealthService linkHealthService;

    public LinkHealthController(LinkHealthService linkHealthService) {
        this.linkHealthService = linkHealthService;
    }

    /**
     * 检测单个短码是否可达；markBroken 为 true 时允许自动标记断链。
     */
    @PostMapping("/health-check/{shortCode}")
    public ApiResponse<HealthCheckResponse> healthCheck(@PathVariable String shortCode, @RequestParam(defaultValue = "false") boolean markBroken) {
        return ApiResponse.success(linkHealthService.healthCheck(shortCode, markBroken));
    }

    /**
     * 批量检测短码是否可达；每个短码独立返回检测结果。
     */
    @PostMapping("/batch-health-check")
    public ApiResponse<List<HealthCheckResponse>> batchHealthCheck(@RequestBody BatchHealthCheckRequest request) {
        return ApiResponse.success(linkHealthService.batchHealthCheck(request));
    }
}
