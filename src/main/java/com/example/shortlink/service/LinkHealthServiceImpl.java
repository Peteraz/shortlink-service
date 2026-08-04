package com.example.shortlink.service;

import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.BatchHealthCheckRequest;
import com.example.shortlink.dto.response.HealthCheckResponse;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.exception.ShortLinkNotFoundException;
import com.example.shortlink.health.LinkHealthChecker;
import com.example.shortlink.repository.ShortLinkRepository;
import com.example.shortlink.validator.ShortCodeValidator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class LinkHealthServiceImpl implements LinkHealthService {

    /**
     * 单次批量检测允许的最大短码数量。
     */
    private static final int MAX_BATCH_SIZE = 100;
    /**
     * 自动断链时写入的统一原因。
     */
    private static final String AUTOMATIC_BROKEN_REASON =
            "automatic health check: all original URLs are unreachable";

    /**
     * 短链内存仓储。
     */
    private final ShortLinkRepository repository;
    /**
     * 原始 URL 可达性检测器。
     */
    private final LinkHealthChecker linkHealthChecker;
    /**
     * 短码格式校验器。
     */
    private final ShortCodeValidator shortCodeValidator;
    /**
     * 批量检测专用有界线程池。
     */
    private final Executor healthCheckExecutor;
    /**
     * 统一时间来源，便于测试固定检测时间。
     */
    private final Clock clock;
    /**
     * 集中管理状态迁移规则。
     */
    private final LinkStatusPolicy linkStatusPolicy;

    public LinkHealthServiceImpl(
            ShortLinkRepository repository,
            LinkHealthChecker linkHealthChecker,
            ShortCodeValidator shortCodeValidator,
            @Qualifier("healthCheckExecutor") Executor healthCheckExecutor,
            Clock clock) {
        this.repository = repository;
        this.linkHealthChecker = linkHealthChecker;
        this.shortCodeValidator = shortCodeValidator;
        this.healthCheckExecutor = healthCheckExecutor;
        this.clock = clock;
        this.linkStatusPolicy = new LinkStatusPolicy();
    }

    /**
     * 检测单条短链；是否自动转为 BROKEN 由 markBroken 和状态策略共同决定。
     */
    @Override
    public HealthCheckResponse healthCheck(String shortCode, boolean markBroken) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = findByShortCode(shortCode);
        return checkLink(shortLink, markBroken);
    }

    /**
     * 去重后将每个短码提交到专用线程池，单条异常转换为该条失败结果。
     */
    @Override
    public List<HealthCheckResponse> batchHealthCheck(BatchHealthCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> shortCodes = normalizeBatchShortCodes(request.getShortCodes());
        List<CompletableFuture<HealthCheckResponse>> futures = new ArrayList<>(shortCodes.size());

        for (String shortCode : shortCodes) {
            try {
                futures.add(CompletableFuture.supplyAsync(
                        () -> safeBatchCheck(shortCode, request.isMarkBroken()),
                        healthCheckExecutor));
            } catch (RuntimeException exception) {
                futures.add(CompletableFuture.completedFuture(
                        failureResult(shortCode, "health check task could not be scheduled")));
            }
        }

        List<HealthCheckResponse> results = new ArrayList<>(futures.size());
        for (int index = 0; index < futures.size(); index++) {
            String shortCode = shortCodes.get(index);
            results.add(futures.get(index)
                    .handle((result, exception) -> exception == null
                            ? result
                            : failureResult(shortCode, "health check task failed"))
                    .join());
        }
        return List.copyOf(results);
    }

    private HealthCheckResponse safeBatchCheck(String shortCode, boolean markBroken) {
        // 批量边界隔离业务异常，避免一个短码失败导致整个批次失败。
        try {
            return healthCheck(shortCode, markBroken);
        } catch (RuntimeException exception) {
            if (exception instanceof ShortLinkNotFoundException) {
                return failureResult(shortCode, "short link not found");
            }
            if (exception instanceof BusinessException) {
                return failureResult(shortCode, exception.getMessage());
            }
            return failureResult(shortCode, "health check task failed");
        }
    }

    private HealthCheckResponse checkLink(ShortLink shortLink, boolean markBroken) {
        // 普通短链只有一个 URL；盲盒检测全部候选，任一可达即可判定整体可达。
        List<UrlHealthResult> urlResults = shortLink.getOriginalUrls().stream().map(this::safeCheck).toList();
        boolean reachable = urlResults.stream().anyMatch(UrlHealthResult::isReachable);
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        shortLink.markCheckedAt(checkedAt);

        boolean markedBroken = markBroken && !reachable && linkStatusPolicy.markBrokenIfAllowed(shortLink, AUTOMATIC_BROKEN_REASON);
        return new HealthCheckResponse(
                shortLink.getShortCode(),
                reachable,
                selectOverallStatus(urlResults),
                overallMessage(urlResults, reachable),
                checkedAt,
                markedBroken,
                urlResults);
    }

    private UrlHealthResult safeCheck(String url) {
        // LinkHealthChecker 负责网络异常转结果；这里再兜底隔离实现异常。
        long startedAt = System.nanoTime();
        try {
            return linkHealthChecker.check(url);
        } catch (RuntimeException exception) {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            return new UrlHealthResult(url, false, null, "health check failed", elapsedMillis);
        }
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

    private String overallMessage(List<UrlHealthResult> urlResults, boolean reachable) {
        if (!reachable) {
            return "all original URLs are unreachable";
        }
        if (urlResults.stream().allMatch(UrlHealthResult::isReachable)) {
            return "all original URLs are reachable";
        }
        return "at least one original URL is reachable";
    }

    private List<String> normalizeBatchShortCodes(List<String> requestedShortCodes) {
        // LinkedHashSet 同时完成去重和保持调用方提交顺序。
        if (requestedShortCodes == null || requestedShortCodes.isEmpty()) {
            throw new BusinessException("INVALID_BATCH_SHORT_CODES", "shortCodes must not be empty");
        }
        if (requestedShortCodes.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(
                    "INVALID_BATCH_SHORT_CODES",
                    "shortCodes must not contain more than " + MAX_BATCH_SIZE + " items");
        }

        Set<String> uniqueShortCodes = new LinkedHashSet<>();
        for (String shortCode : requestedShortCodes) {
            shortCodeValidator.validate(shortCode);
            uniqueShortCodes.add(shortCode);
        }
        return List.copyOf(uniqueShortCodes);
    }

    private HealthCheckResponse failureResult(String shortCode, String message) {
        return new HealthCheckResponse(
                shortCode,
                false,
                null,
                message,
                LocalDateTime.now(clock),
                false,
                List.of());
    }

    private ShortLink findByShortCode(String shortCode) {
        return repository.findByShortCode(shortCode).orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
    }
}
