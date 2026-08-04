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
     * 单个短码的可达性检查，markBroken可选是否自动转为BROKEN状态。
     */
    @PostMapping("/health-check/{shortCode}")
    public ApiResponse<HealthCheckResponse> healthCheck(@PathVariable String shortCode, @RequestParam(defaultValue = "false") boolean markBroken) {
        return ApiResponse.success(linkHealthService.healthCheck(shortCode, markBroken));
    }

    /**
     * 批量短码的可达性检查，markBroken可选是否自动转为BROKEN状态。
     */
    @PostMapping("/batch-health-check")
    public ApiResponse<List<HealthCheckResponse>> batchHealthCheck(@RequestBody BatchHealthCheckRequest request) {
        return ApiResponse.success(linkHealthService.batchHealthCheck(request));
    }
}
