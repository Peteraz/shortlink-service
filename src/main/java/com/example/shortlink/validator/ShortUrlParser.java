package com.example.shortlink.validator;

import com.example.shortlink.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 从本服务生成的完整短链 URL 中提取短码。
 */
@Component
public class ShortUrlParser {

    /**
     * 从完整短链的路径最后一段提取短码；短码格式由 {@link ShortCodeValidator} 校验。
     */
    public String extractShortCode(String shortUrl) {
        try {
            String path = new URI(shortUrl.trim()).getPath();
            int lastSlash = path.lastIndexOf('/');
            String shortCode = path.substring(lastSlash + 1);
            if (shortCode.isEmpty()) {
                throw new InvalidUrlException("short URL must contain a short code");
            }
            return shortCode;
        } catch (URISyntaxException | IllegalArgumentException | NullPointerException exception) {
            throw new InvalidUrlException("short URL must be a valid URI");
        }
    }
}
