package com.example.shortlink.generator;

import com.example.shortlink.config.ShortLinkProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;

@Component
public class Base62ShortCodeGenerator implements ShortCodeGenerator {

    /**
     * 短码使用的 62 个字符集合。
     */
    public static final String BASE62_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 为兼容第一阶段显式长度测试而保留的常量。
     * 应用默认长度由 ShortLinkProperties 控制，当前为 7 位。
     */
    public static final int DEFAULT_LENGTH = 7;

    /**
     * 生成密码学安全随机数的随机源。
     */
    private final SecureRandom secureRandom;
    /**
     * 当前短码长度。
     */
    private final int codeLength;

    @Autowired
    public Base62ShortCodeGenerator(ShortLinkProperties properties) {
        this(new SecureRandom(), properties.getCodeLength());
    }

    public Base62ShortCodeGenerator(SecureRandom secureRandom, int codeLength) {
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom must not be null");
        if (!ShortLinkProperties.isSupportedCodeLength(codeLength)) {
            throw new IllegalArgumentException("codeLength must be one of 6, 7, or 8");
        }
        this.codeLength = codeLength;
    }

    @Override
    public String generate() {
        StringBuilder code = new StringBuilder(codeLength);
        for (int index = 0; index < codeLength; index++) {
            int characterIndex = secureRandom.nextInt(BASE62_CHARACTERS.length());
            code.append(BASE62_CHARACTERS.charAt(characterIndex));
        }
        return code.toString();
    }
}
