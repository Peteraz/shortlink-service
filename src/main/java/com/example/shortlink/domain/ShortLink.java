package com.example.shortlink.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 普通短链和盲盒短链共用的内存领域对象。
 *
 * <p>可变计数使用原子类型，状态使用 volatile，保证并发请求可以安全地观察和更新内存对象。
 * 盲盒次数通过 CAS 循环扣减，避免剩余次数减到负数。</p>
 */
public final class ShortLink {

    /**
     * 未传入渠道时使用的默认渠道。
     */
    public static final String DEFAULT_CHANNEL = "default";

    /**
     * 短链短码。
     */
    private final String shortCode;
    /**
     * 短链类型。
     */
    private final LinkType type;
    /**
     * 原始长链接列表；创建后保存为不可变副本。
     */
    private final List<String> originalUrls;
    /**
     * 归一化后的渠道。
     */
    private final String channel;
    /**
     * 短链创建时间。
     */
    private final LocalDateTime createdAt;
    /**
     * 原子解析次数，仅供领域对象内部并发更新。
     */
    private final AtomicLong resolveCount;
    /**
     * 短链当前状态。
     */
    private volatile LinkStatus status;
    /**
     * 盲盒剩余有效次数；普通短链没有该计数器。
     */
    private final AtomicInteger remainingTimes;
    /**
     * 主动或自动断链时记录的原因。
     */
    private volatile String brokenReason;
    /**
     * 最近一次可达性检测时间。
     */
    private volatile LocalDateTime lastCheckedAt;
    /**
     * 串行化同一条短链的状态检查和状态变更，避免解析与断链标记之间出现检查后失效的窗口。
     */
    private final ReentrantLock stateLock = new ReentrantLock();

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

    /**
     * 普通短链只有一个目标 URL，不维护盲盒有效次数。
     */
    public static ShortLink normal(String shortCode, String originalUrl, String channel, LocalDateTime createdAt) {
        return new ShortLink(shortCode, LinkType.NORMAL, List.of(originalUrl), channel, createdAt, null);
    }

    /**
     * 盲盒保存候选 URL 的不可变副本，并以 validTimes 初始化原子计数器。
     */
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

    /**
     * 返回防御性复制后的不可变 URL 列表。
     */
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
     * 在当前短链的状态锁内执行操作。
     *
     * <p>解析和断链标记必须使用同一把锁，才能保证状态检查与后续动作不可被另一方插入。</p>
     */
    public <T> T withStateLock(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        stateLock.lock();
        try {
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 尝试使用一次盲盒解析次数。
     *
     * <p>该方法只由盲盒解析流程调用。返回 true 表示本次请求成功获得解析资格；返回 false 表示次数已经用完。
     * 最后一次次数使用成功后，会把短链自动标记为 BROKEN。</p>
     */
    public boolean tryConsume() {
        return withStateLock(() -> {
            boolean consumed = tryConsume(remainingTimes);
            if (consumed && remainingTimes.get() == 0) {
                markBrokenByExhaustion();
            }
            return consumed;
        });
    }

    /**
     * 在并发请求下安全地扣减一次剩余次数。
     */
    private static boolean tryConsume(AtomicInteger remainingTimes) {
        while (true) {
            int current = remainingTimes.get();
            if (current <= 0) {
                // 没有剩余次数，扣减失败。
                return false;
            }

            if (remainingTimes.compareAndSet(current, current - 1)) {
                return true;
            }
            // 其他线程已经先修改了次数，重新读取最新值后再尝试扣减。
        }
    }

    public String getBrokenReason() {
        return brokenReason;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    /**
     * 只记录断链原因和状态，是否允许该状态迁移由 LinkStatusPolicy 决定。
     */
    public void markBroken(String reason) {
        withStateLock(() -> {
            this.brokenReason = reason;
            this.status = LinkStatus.BROKEN;
            return null;
        });
    }

    /**
     * 盲盒最后一次解析次数用完后，按照需求自动标记为断链。
     */
    public void markBrokenByExhaustion() {
        withStateLock(() -> {
            if (type != LinkType.BLIND_BOX) {
                throw new IllegalStateException("normal short links cannot be marked broken by exhaustion");
            }
            if (remainingTimes.get() != 0) {
                throw new IllegalStateException("blind-box can be marked broken by exhaustion only when remaining times are zero");
            }
            this.brokenReason = "blind-box valid times exhausted";
            this.status = LinkStatus.BROKEN;
            return null;
        });
    }

    /**
     * 判断盲盒是否已经没有剩余解析次数。
     */
    public boolean hasNoRemainingTimes() {
        return type == LinkType.BLIND_BOX && remainingTimes.get() == 0;
    }

    public void markCheckedAt(LocalDateTime checkedAt) {
        withStateLock(() -> {
            this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
            return null;
        });
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
