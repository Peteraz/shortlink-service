package com.example.shortlink.service;

import com.example.shortlink.config.HealthCheckProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.HealthCheckBusyException;
import com.example.shortlink.exception.ShortLinkNotFoundException;
import com.example.shortlink.health.LinkHealthChecker;
import com.example.shortlink.repository.ShortLinkRepository;
import com.example.shortlink.validator.ShortCodeValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class LinkHealthServiceImpl implements LinkHealthService {

    /**
     * 单次同步批量检测允许提交的最大短链数量。
     */
    private static final int MAX_BATCH_SIZE = 20;
    /**
     * 单次批量请求展开后允许探测的原始 URL 总数，按短链配置计数而不是去重后计数。
     */
    private static final int MAX_BATCH_PROBE_URLS = 32;
    private static final String AUTOMATIC_BROKEN_REASON =
            "automatic health check: all original URLs are unreachable";

    private final ShortLinkRepository repository;
    private final LinkHealthChecker linkHealthChecker;
    private final ShortCodeValidator shortCodeValidator;
    /**
     * 原始 URL 任务共用一个全局有界线程池，避免嵌套线程池与线程饥饿。
     */
    private final Executor urlProbeExecutor;
    private final Clock clock;
    private final LinkStatusPolicy linkStatusPolicy;
    private final int batchTimeoutMillis;

    public LinkHealthServiceImpl(
            ShortLinkRepository repository,
            LinkHealthChecker linkHealthChecker,
            ShortCodeValidator shortCodeValidator,
            @Qualifier("urlProbeExecutor") Executor urlProbeExecutor,
            Clock clock,
            HealthCheckProperties properties) {
        this.repository = repository;
        this.linkHealthChecker = linkHealthChecker;
        this.shortCodeValidator = shortCodeValidator;
        this.urlProbeExecutor = urlProbeExecutor;
        this.clock = clock;
        this.linkStatusPolicy = new LinkStatusPolicy();
        this.batchTimeoutMillis = properties.getBatchTimeoutMillis();
    }

    @Override
    public HealthCheckResponse healthCheck(String shortCode, boolean markBroken) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = findByShortCode(shortCode);
        long deadlineNanos = deadlineAfter(batchTimeoutMillis);
        Map<String, CompletableFuture<SafeCheckResult>> futures = submitProbes(shortLink.getOriginalUrls());
        Map<String, SafeCheckResult> results = awaitProbes(futures, deadlineNanos);
        return aggregate(shortLink, results, markBroken);
    }

    /**
     * 批量检测先完整校验规模，再把全部短链展开成 URL 任务。
     * 同一批次完全相同的 URL 只探测一次，结果再按每条短链原有顺序回填。
     */
    @Override
    public List<HealthCheckResponse> batchHealthCheck(List<String> requestedShortCodes, boolean markBroken) {
        long deadlineNanos = deadlineAfter(batchTimeoutMillis);
        List<String> shortCodes = normalizeBatchShortCodes(requestedShortCodes);
        List<PreparedLink> preparedLinks = prepareLinks(shortCodes);

        List<String> allOriginalUrls = preparedLinks.stream()
                .filter(PreparedLink::found)
                .flatMap(item -> item.shortLink().getOriginalUrls().stream())
                .toList();
        if (allOriginalUrls.size() > MAX_BATCH_PROBE_URLS) {
            throw new BusinessException("BATCH_PROBE_LIMIT_EXCEEDED",
                    "batch health check must not probe more than " + MAX_BATCH_PROBE_URLS + " original URLs");
        }

        Map<String, CompletableFuture<SafeCheckResult>> futures = submitProbes(allOriginalUrls);
        Map<String, SafeCheckResult> probeResults = awaitProbes(futures, deadlineNanos);

        List<HealthCheckResponse> results = new ArrayList<>(preparedLinks.size());
        for (PreparedLink item : preparedLinks) {
            results.add(item.found()
                    ? aggregate(item.shortLink(), probeResults, markBroken)
                    : failureResult(item.shortCode(), "short link not found"));
        }
        return List.copyOf(results);
    }

    private List<PreparedLink> prepareLinks(List<String> shortCodes) {
        List<PreparedLink> preparedLinks = new ArrayList<>(shortCodes.size());
        for (String shortCode : shortCodes) {
            ShortLink shortLink = repository.findByShortCode(shortCode).orElse(null);
            preparedLinks.add(new PreparedLink(shortCode, shortLink));
        }
        return List.copyOf(preparedLinks);
    }

    /**
     * 使用 LinkedHashMap 同时实现批次内精确 URL 去重和稳定顺序。
     * 任一任务无法入队时取消本次已提交任务，并让控制层统一返回 503。
     */
    private Map<String, CompletableFuture<SafeCheckResult>> submitProbes(List<String> originalUrls) {
        Map<String, CompletableFuture<SafeCheckResult>> futures = new LinkedHashMap<>();
        try {
            for (String originalUrl : originalUrls) {
                futures.computeIfAbsent(originalUrl, url ->
                        CompletableFuture.supplyAsync(() -> safeCheck(url), urlProbeExecutor));
            }
            return futures;
        } catch (RuntimeException exception) {
            futures.values().forEach(future -> future.cancel(true));
            throw new HealthCheckBusyException("health check capacity is exhausted, please retry later");
        }
    }

    /**
     * 所有 Future 共用同一个批量截止时间，而不是每个 Future 各等待一次完整超时。
     * 超时或内部执行失败保留为 URL 级结果，并标记 checkerFailed，防止误自动断链。
     */
    private Map<String, SafeCheckResult> awaitProbes(
            Map<String, CompletableFuture<SafeCheckResult>> futures,
            long deadlineNanos) {
        Map<String, SafeCheckResult> results = new LinkedHashMap<>(futures.size());
        boolean interrupted = false;
        for (Map.Entry<String, CompletableFuture<SafeCheckResult>> entry : futures.entrySet()) {
            String url = entry.getKey();
            CompletableFuture<SafeCheckResult> future = entry.getValue();
            if (interrupted) {
                future.cancel(true);
                results.put(url, probeFailure(url, "health check interrupted"));
                continue;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                future.cancel(true);
                results.put(url, probeFailure(url, "health check batch timed out"));
                continue;
            }
            try {
                results.put(url, future.get(remainingNanos, TimeUnit.NANOSECONDS));
            } catch (TimeoutException exception) {
                future.cancel(true);
                results.put(url, probeFailure(url, "health check batch timed out"));
            } catch (InterruptedException exception) {
                future.cancel(true);
                interrupted = true;
                results.put(url, probeFailure(url, "health check interrupted"));
            } catch (ExecutionException | RuntimeException exception) {
                results.put(url, probeFailure(url, "health check failed"));
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return Map.copyOf(results);
    }

    private HealthCheckResponse aggregate(
            ShortLink shortLink,
            Map<String, SafeCheckResult> probeResults,
            boolean markBroken) {
        List<SafeCheckResult> checkResults = shortLink.getOriginalUrls().stream()
                .map(url -> probeResults.getOrDefault(url, probeFailure(url, "health check result is unavailable")))
                .toList();
        List<UrlHealthResult> urlResults = checkResults.stream().map(SafeCheckResult::result).toList();
        boolean reachable = urlResults.stream().anyMatch(UrlHealthResult::isReachable);
        boolean checkerFailed = checkResults.stream().anyMatch(SafeCheckResult::checkerFailed);
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        shortLink.markCheckedAt(checkedAt);

        boolean markedBroken = markBroken && !reachable && !checkerFailed
                && linkStatusPolicy.markBrokenIfAllowed(shortLink, AUTOMATIC_BROKEN_REASON);
        return new HealthCheckResponse(
                shortLink.getShortCode(),
                reachable,
                selectOverallStatus(urlResults),
                overallMessage(urlResults, reachable, checkerFailed),
                checkedAt,
                markedBroken,
                urlResults);
    }

    private SafeCheckResult safeCheck(String url) {
        long startedAt = System.nanoTime();
        try {
            return new SafeCheckResult(linkHealthChecker.check(url), false);
        } catch (RuntimeException exception) {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            return new SafeCheckResult(
                    new UrlHealthResult(url, false, null, "health check failed", elapsedMillis), true);
        }
    }

    private SafeCheckResult probeFailure(String url, String message) {
        return new SafeCheckResult(new UrlHealthResult(url, false, null, message, 0), true);
    }

    private Integer selectOverallStatus(List<UrlHealthResult> urlResults) {
        return urlResults.stream()
                .filter(result -> result.isReachable() && result.getHttpStatus() != null)
                .map(UrlHealthResult::getHttpStatus)
                .findFirst()
                .orElseGet(() -> urlResults.stream()
                        .map(UrlHealthResult::getHttpStatus)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private String overallMessage(List<UrlHealthResult> urlResults, boolean reachable, boolean checkerFailed) {
        if (!reachable && checkerFailed) {
            return "health check incomplete";
        }
        if (!reachable) {
            return "all original URLs are unreachable";
        }
        if (urlResults.stream().allMatch(UrlHealthResult::isReachable)) {
            return "all original URLs are reachable";
        }
        return "at least one original URL is reachable";
    }

    private List<String> normalizeBatchShortCodes(List<String> requestedShortCodes) {
        if (requestedShortCodes == null || requestedShortCodes.isEmpty()) {
            throw new BusinessException("INVALID_BATCH_SHORT_CODES", "shortCodes must not be empty");
        }
        if (requestedShortCodes.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("INVALID_BATCH_SHORT_CODES",
                    "shortCodes must not contain more than " + MAX_BATCH_SIZE + " items");
        }

        Set<String> uniqueShortCodes = new LinkedHashSet<>();
        for (String shortCode : requestedShortCodes) {
            shortCodeValidator.validate(shortCode);
            uniqueShortCodes.add(shortCode);
        }
        return List.copyOf(uniqueShortCodes);
    }

    private long deadlineAfter(int timeoutMillis) {
        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long now = System.nanoTime();
        long deadline = now + timeoutNanos;
        return deadline < now ? Long.MAX_VALUE : deadline;
    }

    private HealthCheckResponse failureResult(String shortCode, String message) {
        return new HealthCheckResponse(
                shortCode, false, null, message, LocalDateTime.now(clock), false, List.of());
    }

    private ShortLink findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
    }

    private record SafeCheckResult(UrlHealthResult result, boolean checkerFailed) {
    }

    private record PreparedLink(String shortCode, ShortLink shortLink) {
        private boolean found() {
            return shortLink != null;
        }
    }
}
