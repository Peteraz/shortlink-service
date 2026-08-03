package com.example.shortlink.validator;

import com.example.shortlink.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @Test
    void shouldNormalizeSchemeAndHostWithoutChangingPathQueryOrFragment() {
        String normalized = validator.validateAndNormalize(
                "HTTPS://EXAMPLE.COM/Some/Path?Query=Value#Fragment");

        assertEquals("https://example.com/Some/Path?Query=Value#Fragment", normalized);
    }

    @Test
    void shouldRejectUnsupportedProtocols() {
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("ftp://example.com/file"));
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("javascript:alert(1)"));
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("file:///tmp/a.txt"));
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("data:text/plain,hello"));
    }

    @Test
    void shouldRejectMissingHostAndOverlongUrl() {
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("https:///path"));
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("https://example.com/" + "a".repeat(2048)));
    }
}
