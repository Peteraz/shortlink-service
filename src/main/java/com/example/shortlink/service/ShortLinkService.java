package com.example.shortlink.service;

import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.response.ShortLinkResponse;

public interface ShortLinkService {

    ShortLinkResponse createNormalLink(CreateNormalLinkRequest request);

    ShortLinkResponse getByShortCode(String shortCode);
}
