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
    void shouldTrimInputAndPreserveUserInfoAndPortWhileNormalizingHost() {
        String normalized = validator.validateAndNormalize(
                "  HTTPS://User:Pass@Example.COM:8443/path?x=1#top  ");

        assertEquals("https://User:Pass@example.com:8443/path?x=1#top", normalized);
    }

    @Test
    void shouldNormalizeIpv6HostAndPreservePort() {
        String normalized = validator.validateAndNormalize(
                "HTTPS://[2001:DB8::1]:8443/Path?x=1#top");

        assertEquals("https://[2001:db8::1]:8443/Path?x=1#top", normalized);
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

    @Test
    void shouldAcceptUrlAtLengthLimitAndRejectBlankOrMalformedUrls() {
        String urlAtLimit = "https://example.com/" + "a".repeat(2028);

        assertEquals(urlAtLimit, validator.validateAndNormalize(urlAtLimit));
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(null));
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize(""));
        assertThrows(InvalidUrlException.class, () -> validator.validateAndNormalize("   "));
        assertThrows(InvalidUrlException.class,
                () -> validator.validateAndNormalize("https://example .com/path"));
    }
}
