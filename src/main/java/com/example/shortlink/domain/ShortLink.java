package com.example.shortlink.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory aggregate for both normal and blind-box short links.
 *
 * <p>The mutable counters use atomic types and status is volatile so request
 * threads can safely observe and update the aggregate in memory. Blind-box
 * consumption uses a compare-and-set loop so the remaining times cannot be
 * decremented below zero.</p>
 */
public final class ShortLink {

    public static final String DEFAULT_CHANNEL = "default";

    private final String shortCode;
    private final LinkType type;
    private final List<String> originalUrls;
    private final String channel;
    private final LocalDateTime createdAt;
    private final AtomicLong resolveCount;
    private volatile LinkStatus status;
    private final AtomicInteger remainingTimes;
    private volatile String brokenReason;
    private volatile LocalDateTime lastCheckedAt;

    private ShortLink(
            String shortCode,
            LinkType type,
            List<String> originalUrls,
            String channel,
            LocalDateTime createdAt,
            Integer remainingTimes) {
        this.shortCode = requireText(shortCode, "shortCode");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.originalUrls = copyAndValidateUrls(originalUrls);
        this.channel = normalizeChannel(channel);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.resolveCount = new AtomicLong(0);
        this.status = LinkStatus.ACTIVE;

        if (type == LinkType.NORMAL && remainingTimes != null) {
            throw new IllegalArgumentException("normal short links cannot have remaining times");
        }
        if (type == LinkType.BLIND_BOX && (remainingTimes == null || remainingTimes <= 0)) {
            throw new IllegalArgumentException("blind-box remaining times must be greater than zero");
        }
        this.remainingTimes = remainingTimes == null ? null : new AtomicInteger(remainingTimes);
    }

    public static ShortLink normal(
            String shortCode,
            String originalUrl,
            String channel,
            LocalDateTime createdAt) {
        return new ShortLink(shortCode, LinkType.NORMAL, List.of(originalUrl), channel, createdAt, null);
    }

    public static ShortLink blindBox(
            String shortCode,
            List<String> originalUrls,
            String channel,
            LocalDateTime createdAt,
            int remainingTimes) {
        if (originalUrls == null || originalUrls.size() < 2) {
            throw new IllegalArgumentException("blind-box original URLs must contain at least two items");
        }
        return new ShortLink(shortCode, LinkType.BLIND_BOX, originalUrls, channel, createdAt, remainingTimes);
    }

    public String getShortCode() {
        return shortCode;
    }

    public LinkType getType() {
        return type;
    }

    /** Returns the defensive immutable URL list. */
    public List<String> getOriginalUrls() {
        return originalUrls;
    }

    public String getChannel() {
        return channel;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public AtomicLong getResolveCount() {
        return resolveCount;
    }

    public LinkStatus getStatus() {
        return status;
    }

    public AtomicInteger getRemainingTimes() {
        return remainingTimes;
    }

    /**
     * Atomically consumes one blind-box resolution.
     *
     * <p>The status is updated only after a successful CAS. Callers must use
     * the boolean result as the source of truth for whether resolution is
     * allowed; status is a secondary state for visibility and fast failure.</p>
     */
    public boolean tryConsume() {
        if (remainingTimes == null) {
            throw new IllegalStateException("normal short links do not have remaining times");
        }

        boolean consumed = tryConsume(remainingTimes);
        if (consumed && remainingTimes.get() == 0) {
            markExhausted();
        }
        return consumed;
    }

    private static boolean tryConsume(AtomicInteger remainingTimes) {
        while (true) {
            int current = remainingTimes.get();
            if (current <= 0) {
                return false;
            }

            // A plain get followed by decrementAndGet is not atomic: multiple
            // threads can observe the same positive value and all decrement.
            if (remainingTimes.compareAndSet(current, current - 1)) {
                return true;
            }
            // Another thread won the race; reread the latest value and retry.
        }
    }

    public String getBrokenReason() {
        return brokenReason;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void markBroken(String reason) {
        this.brokenReason = reason;
        this.status = LinkStatus.BROKEN;
    }

    public void markExhausted() {
        if (type != LinkType.BLIND_BOX) {
            throw new IllegalStateException("normal short links cannot be exhausted");
        }
        this.status = LinkStatus.EXHAUSTED;
    }

    public void markCheckedAt(LocalDateTime checkedAt) {
        this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    private static List<String> copyAndValidateUrls(List<String> urls) {
        Objects.requireNonNull(urls, "originalUrls must not be null");
        if (urls.isEmpty()) {
            throw new IllegalArgumentException("originalUrls must not be empty");
        }
        return List.copyOf(urls.stream()
                .map(url -> requireText(url, "originalUrl"))
                .toList());
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return DEFAULT_CHANNEL;
        }
        return channel.trim();
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
