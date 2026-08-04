package com.example.shortlink.service;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.exception.BlindBoxExhaustedException;
import com.example.shortlink.exception.BrokenLinkException;

/**
 * 集中管理短链状态迁移和解析失败规则。
 */
public final class LinkStatusPolicy {

    /**
     * 只有 ACTIVE 状态的短链可以开始解析。
     * 盲盒是否还有可用次数，由后续的次数扣减结果决定。
     */
    public void ensureResolvable(ShortLink shortLink) {
        if (shortLink.getStatus() == LinkStatus.ACTIVE) {
            return;
        }
        throw failureFor(shortLink);
    }

    public boolean isBroken(ShortLink shortLink) {
        return shortLink.getStatus() == LinkStatus.BROKEN;
    }

    public void ensureCanMarkBroken(ShortLink shortLink) {
        if (shortLink.getStatus() != LinkStatus.ACTIVE) {
            throw failureFor(shortLink);
        }
    }

    public BlindBoxExhaustedException exhausted(ShortLink shortLink) {
        return new BlindBoxExhaustedException("blind-box short link has no remaining times: " + shortLink.getShortCode());
    }

    public boolean markBrokenIfAllowed(ShortLink shortLink, String reason) {
        return shortLink.withStateLock(() -> {
            // 断链是终态，自动检测只能标记 ACTIVE 链接。
            if (shortLink.getStatus() != LinkStatus.ACTIVE) {
                return false;
            }
            shortLink.markBroken(reason);
            return true;
        });
    }

    private RuntimeException failureFor(ShortLink shortLink) {
        return switch (shortLink.getStatus()) {
            case BROKEN -> shortLink.hasNoRemainingTimes()
                    ? exhausted(shortLink)
                    : new BrokenLinkException("short link is broken: " + shortLink.getShortCode());
            case ACTIVE -> new IllegalStateException("active status must be resolvable");
        };
    }
}
