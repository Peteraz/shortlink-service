package com.example.shortlink.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "short-link")
public class ShortLinkProperties {

    public static final int DEFAULT_CODE_LENGTH = 6;
    public static final int MIN_CODE_LENGTH = 6;
    public static final int MAX_CODE_LENGTH = 8;

    @NotBlank
    private String domain = "http://localhost:8090";

    @Min(value = MIN_CODE_LENGTH, message = "short-link.code-length must be at least 6")
    @Max(value = MAX_CODE_LENGTH, message = "short-link.code-length must be at most 8")
    private int codeLength = DEFAULT_CODE_LENGTH;

    public static boolean isSupportedCodeLength(int codeLength) {
        return codeLength >= MIN_CODE_LENGTH && codeLength <= MAX_CODE_LENGTH;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }
}
