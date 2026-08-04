package com.example.shortlink.validator;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.exception.InvalidShortCodeException;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeValidator {

    /**
     * 校验短码长度和 Base62 字符集。
     *
     * <p>短码生成长度由配置决定，但历史短码可能使用旧长度，因此校验必须接受整个
     * 6 到 8 位兼容范围，不能只接受当前生成长度。</p>
     */
    public void validate(String shortCode) {
        if (shortCode == null
                || !ShortLinkProperties.isSupportedCodeLength(shortCode.length())
                || !isBase62(shortCode)) {
            throw new InvalidShortCodeException(
                    "short code must contain between "
                            + ShortLinkProperties.MIN_CODE_LENGTH
                            + " and "
                            + ShortLinkProperties.MAX_CODE_LENGTH
                            + " Base62 characters");
        }
    }

    private boolean isBase62(String shortCode) {
        return shortCode.chars().allMatch(character ->
                character >= '0' && character <= '9'
                        || character >= 'a' && character <= 'z'
                        || character >= 'A' && character <= 'Z');
    }
}
