package com.example.shortlink.service;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.example.shortlink.health.LinkHealthChecker;
import com.example.shortlink.repository.InMemoryShortLinkRepository;
import com.example.shortlink.validator.ShortCodeValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkHealthServicePhaseFiveTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldCheckWithoutMarkingWhenMarkBrokenIsFalse() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/good", "wechat", Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime()));
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, true, 200, "ok", 1));

        HealthCheckResponse response = service.healthCheck("abc123", false);

        assertTrue(response.isReachable());
        assertFalse(response.isMarkedBroken());
        assertEquals(LinkStatus.ACTIVE, repository.findByShortCode("abc123").orElseThrow().getStatus());
        assertNotNull(repository.findByShortCode("abc123").orElseThrow().getLastCheckedAt());
    }

    @Test
    void shouldMarkUnreachableNormalLinkBroken() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/bad", "wechat", Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime()));
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, false, 500, "server error", 2));

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertFalse(response.isReachable());
        assertTrue(response.isMarkedBroken());
        ShortLink link = repository.findByShortCode("abc123").orElseThrow();
        assertEquals(LinkStatus.BROKEN, link.getStatus());
        assertEquals("automatic health check: all original URLs are unreachable", link.getBrokenReason());
    }

    @Test
    void shouldNotMarkBlindBoxWhenAtLeastOneUrlIsReachable() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.blindBox(
                "abc123",
                List.of("https://example.com/good", "https://example.com/bad"),
                "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                10));
        LinkHealthService service = createService(repository, url ->
                new UrlHealthResult(url, url.endsWith("good"), url.endsWith("good") ? 200 : 500, "checked", 1));

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertTrue(response.isReachable());
        assertFalse(response.isMarkedBroken());
        assertEquals(2, response.getUrlResults().size());
        assertEquals(LinkStatus.ACTIVE, repository.findByShortCode("abc123").orElseThrow().getStatus());
    }

    @Test
    void shouldMarkBlindBoxWhenAllUrlsAreUnreachable() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.blindBox(
                "abc123",
                List.of("https://example.com/one", "https://example.com/two"),
                "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                10));
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, false, 404, "not found", 1));

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertFalse(response.isReachable());
        assertTrue(response.isMarkedBroken());
        assertEquals(LinkStatus.BROKEN, repository.findByShortCode("abc123").orElseThrow().getStatus());
    }

    @Test
    void shouldLeaveExhaustedBlindBoxUnchanged() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLink link = ShortLink.blindBox(
                "abc123",
                List.of("https://example.com/one", "https://example.com/two"),
                "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                1);
        assertTrue(link.tryConsume());
        repository.saveIfAbsent("abc123", link);
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, false, 500, "error", 1));

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertFalse(response.isReachable());
        assertFalse(response.isMarkedBroken());
        assertEquals(LinkStatus.EXHAUSTED, link.getStatus());
        assertNotNull(link.getLastCheckedAt());
    }

    @Test
    void shouldReturnPartialBatchResultsAndSurviveTaskFailure() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/good", "wechat", FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        repository.saveIfAbsent("def456", ShortLink.normal(
                "def456", "https://example.com/throws", "wechat", FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        LinkHealthService service = createService(repository, url -> {
            if (url.endsWith("throws")) {
                throw new IllegalStateException("test failure");
            }
            return new UrlHealthResult(url, true, 200, "ok", 1);
        });

        List<HealthCheckResponse> results = service.batchHealthCheck(
                new BatchHealthCheckRequest(List.of("abc123", "abc123", "zzz999", "def456"), false));

        assertEquals(3, results.size());
        assertEquals("abc123", results.get(0).getShortCode());
        assertFalse(results.get(1).isReachable());
        assertEquals("short link not found", results.get(1).getMessage());
        assertFalse(results.get(2).isReachable());
        assertEquals("health check failed", results.get(2).getUrlResults().getFirst().getMessage());
    }

    private LinkHealthService createService(InMemoryShortLinkRepository repository, LinkHealthChecker checker) {
        ShortLinkProperties properties = new ShortLinkProperties();
        return new LinkHealthServiceImpl(
                repository,
                checker,
                new ShortCodeValidator(),
                Runnable::run,
                FIXED_CLOCK);
    }
}
