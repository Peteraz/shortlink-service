package com.example.shortlink.validator;

import com.example.shortlink.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Component
public class UrlValidator {

    public void validate(String url) {
        validateAndNormalize(url);
    }

    public String validateAndNormalize(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("long URL must not be blank");
        }

        String trimmedUrl = url.trim();
        if (trimmedUrl.length() > 2048) {
            throw new InvalidUrlException("long URL must not exceed 2048 characters");
        }

        try {
            URI uri = new URI(trimmedUrl);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new InvalidUrlException("long URL must use http or https and contain a host");
            }
            return normalizeSchemeAndHost(uri);
        } catch (URISyntaxException exception) {
            throw new InvalidUrlException("long URL is not a valid URI");
        }
    }

    /**
     * 规范化协议和 host
     * 例如 HTTPS://User:Pass@Example.COM:8443/path?x=1#top
     * 规范化 https://User:Pass@example.com:8443/path?x=1#top
     */
    private String normalizeSchemeAndHost(URI uri) {
        String rawAuthority = uri.getRawAuthority();
        String userInfo = "";
        String hostAndPort = rawAuthority;
        int userInfoSeparator = rawAuthority.lastIndexOf('@');
        if (userInfoSeparator >= 0) {
            userInfo = rawAuthority.substring(0, userInfoSeparator + 1);
            hostAndPort = rawAuthority.substring(userInfoSeparator + 1);
        }

        String normalizedHostAndPort;
        if (hostAndPort.startsWith("[")) {
            int closingBracket = hostAndPort.indexOf(']');
            String normalizedIpv6Host = hostAndPort.substring(1, closingBracket).toLowerCase(Locale.ROOT);
            normalizedHostAndPort = "[" + normalizedIpv6Host + "]"
                    + hostAndPort.substring(closingBracket + 1);
        } else {
            String normalizedHost = uri.getHost().toLowerCase(Locale.ROOT);
            int portSeparator = hostAndPort.lastIndexOf(':');
            normalizedHostAndPort = portSeparator > 0
                    ? normalizedHost + hostAndPort.substring(portSeparator)
                    : normalizedHost;
        }

        StringBuilder normalized = new StringBuilder()
                .append(uri.getScheme().toLowerCase(Locale.ROOT))
                .append("://")
                .append(userInfo)
                .append(normalizedHostAndPort);
        if (uri.getRawPath() != null) {
            normalized.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            normalized.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            normalized.append('#').append(uri.getRawFragment());
        }
        return normalized.toString();
    }
}
