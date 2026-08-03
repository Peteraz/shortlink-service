package com.example.shortlink.service;

import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.dto.response.ResolveResult;

public interface ShortLinkService {

    ShortLinkResponse createNormalLink(CreateNormalLinkRequest request);

    ShortLinkResponse createBlindBoxLink(CreateBlindBoxLinkRequest request);

    ShortLinkResponse getByShortCode(String shortCode);

    ResolveResult resolve(String shortCode);

    ShortLinkResponse markBroken(String shortCode, String reason);

    PageResponse<ShortLinkResponse> query(ShortLinkQuery query);
}
