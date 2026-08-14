package com.example.shortlink.controller;

import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.MarkBrokenRequest;
import com.example.shortlink.dto.request.ResolveRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import com.example.shortlink.service.ShortLinkService;
import com.example.shortlink.validator.ShortUrlParser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/short-links")
public class ShortLinkController {

    /**
     * 短链创建、查询、解析和断链服务。
     */
    private final ShortLinkService shortLinkService;
    private final ShortUrlParser shortUrlParser;

    public ShortLinkController(ShortLinkService shortLinkService, ShortUrlParser shortUrlParser) {
        this.shortLinkService = shortLinkService;
        this.shortUrlParser = shortUrlParser;
    }

    /**
     * 创建普通短链。
     */
    @PostMapping("/normal")
    public ApiResponse<ShortLinkResponse> createNormalLink(@RequestBody CreateNormalLinkRequest request) {
        return ApiResponse.success(shortLinkService.createNormalLink(request));
    }

    /**
     * 创建盲盒短链。
     */
    @PostMapping("/blind-box")
    public ApiResponse<ShortLinkResponse> createBlindBoxLink(@RequestBody CreateBlindBoxLinkRequest request) {
        return ApiResponse.success(shortLinkService.createBlindBoxLink(request));
    }

    /**
     * 查询短链详情，不会触发解析。
     */
    @GetMapping("/query/{shortCode}")
    public ApiResponse<ShortLinkResponse> getByShortCode(@PathVariable String shortCode) {
        return ApiResponse.success(shortLinkService.getByShortCode(shortCode));
    }

    /**
     * 根据完整短链解析详情，普通短链计数增加、盲盒短链次数消耗。
     */
    @PostMapping("/resolve")
    public ApiResponse<ResolveResponse> resolve(@Valid @RequestBody ResolveRequest request) {
        String shortCode = shortUrlParser.extractShortCode(request.getShortUrl());
        return ApiResponse.success(shortLinkService.resolve(shortCode));
    }

    /**
     * 手动标记断链；断链原因由 Service 校验和规范化。
     */
    @PatchMapping("/broken/{shortCode}")
    public ApiResponse<ShortLinkResponse> markBroken(@PathVariable String shortCode, @RequestBody MarkBrokenRequest request) {
        return ApiResponse.success(shortLinkService.markBroken(shortCode, request.getReason()));
    }

    /**
     * 查询参数交给 Service 做规范化、过滤、排序和分页。
     */
    @GetMapping("/queryByPage")
    public ApiResponse<PageResponse<ShortLinkResponse>> query(
            @RequestParam(required = false) String shortCode,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) LinkStatus status,
            @RequestParam(required = false) LinkType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ShortLinkQuery query = new ShortLinkQuery(shortCode, channel, status, type, page, size);
        return ApiResponse.success(shortLinkService.query(query));
    }
}
