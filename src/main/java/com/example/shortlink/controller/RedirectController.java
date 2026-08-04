package com.example.shortlink.controller;

import com.example.shortlink.dto.response.ResolveResult;
import com.example.shortlink.service.ShortLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping({"/s", "/api/v1/redirect/s"})
public class RedirectController {

    /**
     * 负责执行短链解析的服务。
     */
    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /**
     * Controller 只负责把 Service 的解析结果转换为 302 和 Location。
     */
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        ResolveResult result = shortLinkService.resolve(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.getTargetUrl()))
                .build();
    }
}
