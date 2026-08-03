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

    private static final int MAX_BATCH_SIZE = 100;
    private static final String AUTOMATIC_BROKEN_REASON =
            "automatic health check: all original URLs are unreachable";

    private final ShortLinkRepository repository;
    private final LinkHealthChecker linkHealthChecker;
    private final ShortCodeValidator shortCodeValidator;
    private final Executor healthCheckExecutor;
    private final Clock clock;
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

    @Override
    public HealthCheckResponse healthCheck(String shortCode, boolean markBroken) {
        shortCodeValidator.validate(shortCode);
        ShortLink shortLink = findByShortCode(shortCode);
        return checkLink(shortLink, markBroken);
    }

    @Override
    public List<HealthCheckResponse> batchHealthCheck(BatchHealthCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> shortCodes = normalizeBatchShortCodes(request.shortCodes());
        List<CompletableFuture<HealthCheckResponse>> futures = new ArrayList<>(shortCodes.size());

        for (String shortCode : shortCodes) {
            try {
                futures.add(CompletableFuture.supplyAsync(
                        () -> safeBatchCheck(shortCode, request.markBroken()),
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
        List<UrlHealthResult> urlResults = shortLink.getOriginalUrls().stream()
                .map(this::safeCheck)
                .toList();
        boolean reachable = urlResults.stream().anyMatch(UrlHealthResult::reachable);
        LocalDateTime checkedAt = LocalDateTime.now(clock);
        shortLink.markCheckedAt(checkedAt);

        boolean markedBroken = markBroken
                && !reachable
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

    private UrlHealthResult safeCheck(String url) {
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
                .filter(result -> result.reachable() && result.httpStatus() != null)
                .map(UrlHealthResult::httpStatus)
                .findFirst()
                .orElseGet(() -> urlResults.stream()
                        .map(UrlHealthResult::httpStatus)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
    }

    private String overallMessage(List<UrlHealthResult> urlResults, boolean reachable) {
        if (!reachable) {
            return "all original URLs are unreachable";
        }
        if (urlResults.stream().allMatch(UrlHealthResult::reachable)) {
            return "all original URLs are reachable";
        }
        return "at least one original URL is reachable";
    }

    private List<String> normalizeBatchShortCodes(List<String> requestedShortCodes) {
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
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortLinkNotFoundException("short link not found: " + shortCode));
    }
}
