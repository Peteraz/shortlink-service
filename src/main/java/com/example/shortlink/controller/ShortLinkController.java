package com.example.shortlink.controller;

import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.MarkBrokenRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.ApiResponse;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.service.ShortLinkService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
    /**
     * 解析结果到接口响应对象的转换器。
     */
    private final ShortLinkMapper shortLinkMapper;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this(shortLinkService, new ShortLinkMapper(new ShortLinkProperties()));
    }

    @Autowired
    public ShortLinkController(ShortLinkService shortLinkService, ShortLinkMapper shortLinkMapper) {
        this.shortLinkService = shortLinkService;
        this.shortLinkMapper = shortLinkMapper;
    }

    /**
     * 创建普通短链接
     */
    @PostMapping("/normal")
    public ApiResponse<ShortLinkResponse> createNormalLink(@RequestBody CreateNormalLinkRequest request) {
        return ApiResponse.success(shortLinkService.createNormalLink(request));
    }

    /**
     * 创建盲盒短链接
     */
    @PostMapping("/blind-box")
    public ApiResponse<ShortLinkResponse> createBlindBoxLink(@Valid @RequestBody CreateBlindBoxLinkRequest request) {
        return ApiResponse.success(shortLinkService.createBlindBoxLink(request));
    }

    /**
     * 短链详情查询
     */
    @GetMapping("/query/{shortCode}")
    public ApiResponse<ShortLinkResponse> getByShortCode(@PathVariable String shortCode) {
        return ApiResponse.success(shortLinkService.getByShortCode(shortCode));
    }

    /**
     * 解析详情会执行真实解析，因此普通计数增加、盲盒次数消耗。
     */
    @GetMapping("/resolve/{shortCode}")
    public ApiResponse<ResolveResponse> resolve(@PathVariable String shortCode) {
        return ApiResponse.success(shortLinkMapper.toResolveResponse(shortLinkService.resolve(shortCode)));
    }

    /**
     * 断链原因由请求校验
     */
    @PatchMapping("/broken/{shortCode}")
    public ApiResponse<ShortLinkResponse> markBroken(@PathVariable String shortCode, @Valid @RequestBody MarkBrokenRequest request) {
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
