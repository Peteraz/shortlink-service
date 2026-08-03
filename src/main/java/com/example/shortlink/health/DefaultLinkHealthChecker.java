package com.example.shortlink.health;

import com.example.shortlink.config.HealthCheckProperties;
import com.example.shortlink.dto.response.UrlHealthResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

@Component
public class DefaultLinkHealthChecker implements LinkHealthChecker {

    private static final int HTTP_OK_MIN = 200;
    private static final int HTTP_REDIRECT_MAX = 400;
    private static final int METHOD_NOT_ALLOWED = 405;

    private final HttpClient httpClient;
    private final AddressPolicy addressPolicy;
    private final Duration requestTimeout;

    public DefaultLinkHealthChecker(
            HttpClient healthCheckHttpClient,
            AddressPolicy addressPolicy,
            HealthCheckProperties properties) {
        this.httpClient = healthCheckHttpClient;
        this.addressPolicy = addressPolicy;
        this.requestTimeout = Duration.ofMillis(properties.getRequestTimeoutMillis());
    }

    @Override
    public UrlHealthResult check(String url) {
        long startedAt = System.nanoTime();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException exception) {
            return result(url, false, null, "invalid URL", startedAt);
        }

        try {
            addressPolicy.validate(uri);
        } catch (AddressPolicyViolationException exception) {
            return result(url, false, null, exception.getMessage(), startedAt);
        } catch (RuntimeException exception) {
            return result(url, false, null, "request blocked by SSRF security policy", startedAt);
        }

        try {
            HttpRequest headRequest = HttpRequest.newBuilder(uri)
                    .timeout(requestTimeout)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> headResponse = httpClient.send(
                    headRequest,
                    HttpResponse.BodyHandlers.discarding());
            if (headResponse.statusCode() == METHOD_NOT_ALLOWED) {
                return checkWithGet(uri, url, startedAt);
            }
            return result(
                    url,
                    isReachable(headResponse.statusCode()),
                    headResponse.statusCode(),
                    messageForStatus(headResponse.statusCode()),
                    startedAt);
        } catch (HttpTimeoutException exception) {
            return result(url, false, null, "request timed out", startedAt);
        } catch (UnknownHostException exception) {
            return result(url, false, null, "DNS resolution failed", startedAt);
        } catch (ConnectException exception) {
            return result(url, false, null, "connection failed", startedAt);
        } catch (IOException exception) {
            return result(url, false, null, "request failed", startedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return result(url, false, null, "request interrupted", startedAt);
        } catch (RuntimeException exception) {
            return result(url, false, null, "request failed", startedAt);
        }
    }

    private UrlHealthResult checkWithGet(URI uri, String url, long startedAt)
            throws IOException, InterruptedException {
        HttpRequest getRequest = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .GET()
                .build();
        HttpResponse<InputStream> getResponse = httpClient.send(
                getRequest,
                HttpResponse.BodyHandlers.ofInputStream());
        // Reading the response body is unnecessary for reachability. Closing
        // the stream releases the connection without downloading the payload.
        try (InputStream ignored = getResponse.body()) {
            return result(
                    url,
                    isReachable(getResponse.statusCode()),
                    getResponse.statusCode(),
                    messageForStatus(getResponse.statusCode()),
                    startedAt);
        }
    }

    private boolean isReachable(int statusCode) {
        return statusCode >= HTTP_OK_MIN && statusCode < HTTP_REDIRECT_MAX;
    }

    private String messageForStatus(int statusCode) {
        return isReachable(statusCode)
                ? "HTTP status " + statusCode
                : "HTTP status " + statusCode + " is not reachable";
    }

    private UrlHealthResult result(
            String url,
            boolean reachable,
            Integer httpStatus,
            String message,
            long startedAt) {
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return new UrlHealthResult(url, reachable, httpStatus, message, elapsedMillis);
    }
}
