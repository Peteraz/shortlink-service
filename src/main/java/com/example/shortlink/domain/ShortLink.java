package com.example.shortlink.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 普通短链和盲盒短链共用的内存领域对象。
 *
 * <p>状态、解析次数和盲盒剩余次数均由当前短链的状态锁保护，保证并发请求可以安全地观察和更新内存对象。</p>
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
     * 成功解析次数，由状态锁保护。
     */
    private long resolveCount;
    /**
     * 短链当前状态。
     */
    private LinkStatus status;
    /**
     * 盲盒剩余有效次数；普通短链没有该计数器。
     */
    private Integer remainingTimes;
    /**
     * 主动或自动断链时记录的原因。
     */
    private String brokenReason;
    /**
     * 最近一次可达性检测时间。
     */
    private LocalDateTime lastCheckedAt;
    /**
     * 同一短链的状态检查和状态修改共用此锁。
     * 防止一个请求刚确认短链可解析，另一个请求就将它标记为断链。
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
        this.resolveCount = 0;
        this.status = LinkStatus.ACTIVE;

        if (type == LinkType.NORMAL && remainingTimes != null) {
            throw new IllegalArgumentException("normal short links cannot have remaining times");
        }
        if (type == LinkType.BLIND_BOX && (remainingTimes == null || remainingTimes <= 0)) {
            throw new IllegalArgumentException("blind-box remaining times must be greater than zero");
        }
        this.remainingTimes = remainingTimes;
    }

    /**
     * 普通短链只有一个目标 URL，不维护盲盒有效次数。
     */
    public static ShortLink normal(String shortCode, String originalUrl, String channel, LocalDateTime createdAt) {
        return new ShortLink(shortCode, LinkType.NORMAL, List.of(originalUrl), channel, createdAt, null);
    }

    /**
     * 盲盒保存候选 URL 的不可变副本，并以 validTimes 初始化剩余次数。
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

    public long getResolveCount() {
        return withStateLock(() -> resolveCount);
    }

    public LinkStatus getStatus() {
        return withStateLock(() -> status);
    }

    public Integer getRemainingTimes() {
        return withStateLock(() -> remainingTimes);
    }

    /**
     * 在当前短链的状态锁保护范围内执行操作。
     *
     * <p>解析和断链标记使用同一把锁，保证状态检查完成后，后续操作不会被其他请求插入。
     * 当前线程已持有该锁时，直接在现有锁范围内执行，避免重复加锁。</p>
     */
    public <T> T withStateLock(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        if (stateLock.isHeldByCurrentThread()) {
            return action.get();
        }

        stateLock.lock();
        try {
            return action.get();
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * 在当前短链的状态锁内消耗一次盲盒解析次数。
     *
     * <p>返回 {@code true} 表示本次请求获得解析资格；最后一次扣减成功后，
     * 短链会自动标记为 {@link LinkStatus#BROKEN}。</p>
     */
    public boolean tryConsume() {
        return withStateLock(() -> {
            if (remainingTimes <= 0) {
                return false;
            }

            remainingTimes--;
            if (remainingTimes == 0) {
                markBrokenByExhaustion();
            }
            return true;
        });
    }

    public String getBrokenReason() {
        return withStateLock(() -> brokenReason);
    }

    public LocalDateTime getLastCheckedAt() {
        return withStateLock(() -> lastCheckedAt);
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
     * 在当前短链的状态锁内增加一次成功解析次数。
     */
    public void incrementResolveCount() {
        withStateLock(() -> {
            resolveCount++;
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
            if (remainingTimes != 0) {
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
        return withStateLock(() -> type == LinkType.BLIND_BOX && remainingTimes == 0);
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
