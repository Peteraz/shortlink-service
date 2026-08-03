package com.example.shortlink.generator;

import com.example.shortlink.config.ShortLinkProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;

@Component
public class Base62ShortCodeGenerator implements ShortCodeGenerator {

    public static final String BASE62_CHARACTERS =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /**
     * Retained for compatibility with the first-stage explicit-length test.
     * The application default is controlled by ShortLinkProperties and is 6.
     */
    public static final int DEFAULT_LENGTH = 7;

    private final SecureRandom secureRandom;
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
