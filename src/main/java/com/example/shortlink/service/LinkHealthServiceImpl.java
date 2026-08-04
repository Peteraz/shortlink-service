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
    private static final String AUTOMATIC_BROKEN_REASON = "automatic health check: all original URLs are unreachable";

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
     * 批量检测多个短码。
     *
     * <p>方法会先校验并去重短码，再将每个短码提交到专用线程池。
     * 单个短码检测失败时，只返回该短码的失败结果，不影响同一批次中的其他短码。</p>
     */
    @Override
    public List<HealthCheckResponse> batchHealthCheck(BatchHealthCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // 校验短码格式、去重，并保留短码第一次出现时的顺序。
        List<String> shortCodes = normalizeBatchShortCodes(request.getShortCodes());
        List<CompletableFuture<HealthCheckResponse>> futures = new ArrayList<>(shortCodes.size());

        for (String shortCode : shortCodes) {
            try {
                // 为每个短码提交一个独立的异步检测任务。
                futures.add(CompletableFuture.supplyAsync(
                        () -> safeBatchCheck(shortCode, request.isMarkBroken()), healthCheckExecutor));
            } catch (RuntimeException exception) {
                // 任务提交失败时，仅记录当前短码失败，继续处理其他短码。
                futures.add(CompletableFuture.completedFuture(failureResult(shortCode, "health check task could not be scheduled")));
            }
        }

        // 按短码顺序等待任务完成，保证返回结果的顺序稳定。
        List<HealthCheckResponse> results = new ArrayList<>(futures.size());
        for (int index = 0; index < futures.size(); index++) {
            String shortCode = shortCodes.get(index);
            // 任务执行异常转换为当前短码的失败结果，避免中断整个批次。
            results.add(futures.get(index)
                    .handle((result, exception) -> exception == null ? result : failureResult(shortCode, "health check task failed"))
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
        List<SafeCheckResult> checkResults = shortLink.getOriginalUrls().stream().map(this::safeCheck).toList();
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
                overallMessage(urlResults, reachable),
                checkedAt,
                markedBroken,
                urlResults);
    }

    private SafeCheckResult safeCheck(String url) {
        // LinkHealthChecker 负责网络异常转结果；这里再兜底隔离实现异常。
        long startedAt = System.nanoTime();
        try {
            return new SafeCheckResult(linkHealthChecker.check(url), false);
        } catch (RuntimeException exception) {
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            return new SafeCheckResult(
                    new UrlHealthResult(url, false, null, "health check failed", elapsedMillis), true);
        }
    }

    /**
     * 保留检测结果，同时标识检测器自身是否发生异常。
     * 检测器失败不等同于目标 URL 不可达，不能据此自动断链。
     */
    private record SafeCheckResult(UrlHealthResult result, boolean checkerFailed) {
    }

    /**
     * 为多个 URL 的检测结果选择一个代表性的 HTTP 状态码。
     *
     * <p>优先返回第一个可达 URL 的状态码；如果没有可达 URL，
     * 则返回第一个存在的状态码；如果所有请求都没有得到 HTTP 响应，返回 null。</p>
     */
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
            throw new BusinessException("INVALID_BATCH_SHORT_CODES", "shortCodes must not contain more than " + MAX_BATCH_SIZE + " items");
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
