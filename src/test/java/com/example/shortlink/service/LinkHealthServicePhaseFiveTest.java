package com.example.shortlink.service;

import com.example.shortlink.config.HealthCheckProperties;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.HealthCheckBusyException;
import com.example.shortlink.health.LinkHealthChecker;
import com.example.shortlink.repository.InMemoryShortLinkRepository;
import com.example.shortlink.validator.ShortCodeValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void shouldNotMarkLinkBrokenWhenHealthCheckerFailsInternally() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/error", "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        LinkHealthService service = createService(repository, url -> {
            throw new IllegalStateException("test failure");
        });

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertFalse(response.isReachable());
        assertFalse(response.isMarkedBroken());
        assertEquals("health check failed", response.getUrlResults().getFirst().getMessage());
        assertEquals(LinkStatus.ACTIVE, repository.findByShortCode("abc123").orElseThrow().getStatus());
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
    void shouldProbeBlindBoxUrlsConcurrently() throws Exception {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.blindBox(
                "abc123",
                List.of("https://example.com/one", "https://example.com/two", "https://example.com/three"),
                "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(),
                10));
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService urlProbeExecutor = Executors.newFixedThreadPool(3);
        ExecutorService callerExecutor = Executors.newSingleThreadExecutor();
        LinkHealthService service = createService(repository, url -> {
            started.countDown();
            await(release);
            return new UrlHealthResult(url, false, 404, "not found", 1);
        }, urlProbeExecutor, new HealthCheckProperties());

        try {
            CompletableFuture<HealthCheckResponse> responseFuture = CompletableFuture.supplyAsync(
                    () -> service.healthCheck("abc123", true), callerExecutor);

            assertTrue(started.await(1, TimeUnit.SECONDS));
            release.countDown();

            HealthCheckResponse response = responseFuture.get(1, TimeUnit.SECONDS);
            assertFalse(response.isReachable());
            assertTrue(response.isMarkedBroken());
            assertEquals(3, response.getUrlResults().size());
        } finally {
            release.countDown();
            callerExecutor.shutdownNow();
            urlProbeExecutor.shutdownNow();
        }
    }

    @Test
    void shouldLeaveExhaustedBlindBoxBrokenAndUnchanged() {
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
        assertEquals(LinkStatus.BROKEN, link.getStatus());
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
                List.of("abc123", "abc123", "zzz999", "def456"), false);

        assertEquals(3, results.size());
        assertEquals("abc123", results.get(0).getShortCode());
        assertFalse(results.get(1).isReachable());
        assertEquals("short link not found", results.get(1).getMessage());
        assertFalse(results.get(2).isReachable());
        assertEquals("health check failed", results.get(2).getUrlResults().getFirst().getMessage());
    }

    @Test
    void shouldRejectBatchContainingMoreThanTwentyShortCodes() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, true, 200, "ok", 1));
        List<String> shortCodes = java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> String.format("a%05d", index))
                .toList();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.batchHealthCheck(shortCodes, false));

        assertEquals("INVALID_BATCH_SHORT_CODES", exception.getCode());
    }

    @Test
    void shouldAcceptBatchContainingExactlyThirtyTwoConfiguredUrls() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        List<String> shortCodes = saveLinksWithTotalUrlCount(repository, 32);
        AtomicInteger checks = new AtomicInteger();
        LinkHealthService service = createService(repository, url -> {
            checks.incrementAndGet();
            return new UrlHealthResult(url, true, 200, "ok", 1);
        });

        List<HealthCheckResponse> responses = service.batchHealthCheck(shortCodes, false);

        assertEquals(shortCodes.size(), responses.size());
        assertEquals(32, checks.get());
    }

    @Test
    void shouldRejectBatchContainingThirtyThreeConfiguredUrlsBeforeSubmittingTasks() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        List<String> shortCodes = saveLinksWithTotalUrlCount(repository, 33);
        AtomicInteger checks = new AtomicInteger();
        LinkHealthService service = createService(repository, url -> {
            checks.incrementAndGet();
            return new UrlHealthResult(url, true, 200, "ok", 1);
        });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.batchHealthCheck(shortCodes, false));

        assertEquals("BATCH_PROBE_LIMIT_EXCEEDED", exception.getCode());
        assertEquals(0, checks.get());
    }

    @Test
    void shouldProbeIdenticalUrlOnlyOnceWithinBatch() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        String sharedUrl = "https://example.com/shared";
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", sharedUrl, "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        repository.saveIfAbsent("def456", ShortLink.normal(
                "def456", sharedUrl, "douyin",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        AtomicInteger checks = new AtomicInteger();
        LinkHealthService service = createService(repository, url -> {
            checks.incrementAndGet();
            return new UrlHealthResult(url, true, 200, "ok", 1);
        });

        List<HealthCheckResponse> results = service.batchHealthCheck(List.of("abc123", "def456"), false);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(HealthCheckResponse::isReachable));
        assertEquals(1, checks.get());
    }

    @Test
    void shouldFailFastWhenUrlProbeExecutorIsSaturated() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/good", "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("saturated");
        };
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, true, 200, "ok", 1),
                rejectingExecutor, new HealthCheckProperties());

        HealthCheckBusyException exception = assertThrows(HealthCheckBusyException.class,
                () -> service.healthCheck("abc123", false));

        assertEquals("HEALTH_CHECK_BUSY", exception.getCode());
    }

    @Test
    void shouldStopWaitingAtBatchDeadlineWithoutMarkingBroken() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123", "https://example.com/slow", "wechat",
                FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
        HealthCheckProperties properties = new HealthCheckProperties();
        properties.setBatchTimeoutMillis(30);
        Executor neverRunsTasks = command -> {
            // 模拟已接收但迟迟无法开始的排队任务，验证批次只有一个总等待预算。
        };
        LinkHealthService service = createService(repository,
                url -> new UrlHealthResult(url, false, null, "slow", 1),
                neverRunsTasks, properties);

        HealthCheckResponse response = service.healthCheck("abc123", true);

        assertEquals("health check incomplete", response.getMessage());
        assertEquals("health check batch timed out", response.getUrlResults().getFirst().getMessage());
        assertFalse(response.isMarkedBroken());
        assertEquals(LinkStatus.ACTIVE, repository.findByShortCode("abc123").orElseThrow().getStatus());
    }

    private LinkHealthService createService(InMemoryShortLinkRepository repository, LinkHealthChecker checker) {
        return createService(repository, checker, Runnable::run, new HealthCheckProperties());
    }

    private LinkHealthService createService(
            InMemoryShortLinkRepository repository,
            LinkHealthChecker checker,
            Executor urlProbeExecutor,
            HealthCheckProperties properties) {
        return new LinkHealthServiceImpl(
                repository,
                checker,
                new ShortCodeValidator(),
                urlProbeExecutor,
                FIXED_CLOCK,
                properties);
    }

    private List<String> saveLinksWithTotalUrlCount(
            InMemoryShortLinkRepository repository, int totalUrlCount) {
        List<String> shortCodes = new java.util.ArrayList<>();
        int remainingUrlCount = totalUrlCount;
        int linkIndex = 0;
        while (remainingUrlCount > 0) {
            int candidateCount = Math.min(10, remainingUrlCount);
            String shortCode = String.format("b%05d", linkIndex);
            if (candidateCount == 1) {
                repository.saveIfAbsent(shortCode, ShortLink.normal(
                        shortCode,
                        "https://example.com/" + linkIndex + "/0",
                        "wechat",
                        FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime()));
            } else {
                int currentLinkIndex = linkIndex;
                List<String> urls = java.util.stream.IntStream.range(0, candidateCount)
                        .mapToObj(urlIndex ->
                                "https://example.com/" + currentLinkIndex + "/" + urlIndex)
                        .toList();
                repository.saveIfAbsent(shortCode, ShortLink.blindBox(
                        shortCode, urls, "wechat",
                        FIXED_CLOCK.instant().atZone(ZoneOffset.UTC).toLocalDateTime(), 10));
            }
            shortCodes.add(shortCode);
            remainingUrlCount -= candidateCount;
            linkIndex++;
        }
        return List.copyOf(shortCodes);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
