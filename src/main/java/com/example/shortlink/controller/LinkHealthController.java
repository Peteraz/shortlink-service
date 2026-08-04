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

    /**
     * 短链可达性检测服务。
     */
    private final LinkHealthService linkHealthService;

    public LinkHealthController(LinkHealthService linkHealthService) {
        this.linkHealthService = linkHealthService;
    }

    /**
     * 单条检测的状态变更由 Service 决定，Controller 不操作领域状态。
     */
    @PostMapping("/health-check/{shortCode}")
    public ApiResponse<HealthCheckResponse> healthCheck(@PathVariable String shortCode, @RequestParam(defaultValue = "false") boolean markBroken) {
        return ApiResponse.success(linkHealthService.healthCheck(shortCode, markBroken));
    }

    /**
     * 批量请求只负责接收校验后的参数，线程池由 Service 管理。
     */
    @PostMapping("/batch-health-check")
    public ApiResponse<List<HealthCheckResponse>> batchHealthCheck(@Valid @RequestBody BatchHealthCheckRequest request) {
        return ApiResponse.success(linkHealthService.batchHealthCheck(request));
    }
}
