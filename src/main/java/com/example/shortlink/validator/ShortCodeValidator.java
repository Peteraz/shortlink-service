package com.example.shortlink.validator;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.exception.InvalidShortCodeException;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeValidator {

    private final ShortLinkProperties properties;

    public ShortCodeValidator(ShortLinkProperties properties) {
        this.properties = properties;
    }

    public void validate(String shortCode) {
        if (shortCode == null
                || shortCode.length() != properties.getCodeLength()
                || !isBase62(shortCode)) {
            throw new InvalidShortCodeException(
                    "short code must contain exactly " + properties.getCodeLength() + " Base62 characters");
        }
    }

    private boolean isBase62(String shortCode) {
        return shortCode.chars().allMatch(character ->
                character >= '0' && character <= '9'
                        || character >= 'a' && character <= 'z'
                        || character >= 'A' && character <= 'Z');
    }
}
