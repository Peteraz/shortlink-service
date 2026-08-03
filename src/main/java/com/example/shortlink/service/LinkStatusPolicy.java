package com.example.shortlink.service;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.exception.BlindBoxExhaustedException;
import com.example.shortlink.exception.BrokenLinkException;

/**
 * 集中管理短链状态迁移和解析失败规则。
 */
public final class LinkStatusPolicy {

    public void ensureNormalResolvable(ShortLink shortLink) {
        if (shortLink.getStatus() == LinkStatus.ACTIVE) {
            return;
        }
        throw failureFor(shortLink);
    }

    /**
     * 盲盒次数扣减仍以 CAS 结果为最终依据；本方法只在 CAS 前快速拒绝 BROKEN 短链。
     */
    public void ensureBlindNotBroken(ShortLink shortLink) {
        if (shortLink.getStatus() == LinkStatus.BROKEN) {
            throw new BrokenLinkException("short link is broken: " + shortLink.getShortCode());
        }
    }

    public boolean isBroken(ShortLink shortLink) {
        return shortLink.getStatus() == LinkStatus.BROKEN;
    }

    public void ensureCanMarkBroken(ShortLink shortLink) {
        if (shortLink.getStatus() == LinkStatus.EXHAUSTED) {
            throw new BlindBoxExhaustedException(
                    "exhausted short link cannot be marked broken: " + shortLink.getShortCode());
        }
    }

    public BlindBoxExhaustedException exhausted(ShortLink shortLink) {
        return new BlindBoxExhaustedException(
                "blind-box short link has no remaining times: " + shortLink.getShortCode());
    }

    public boolean markBrokenIfAllowed(ShortLink shortLink, String reason) {
        // EXHAUSTED 和 BROKEN 都是终态，自动检测只能标记 ACTIVE 链接。
        if (shortLink.getStatus() != LinkStatus.ACTIVE) {
            return false;
        }
        shortLink.markBroken(reason);
        return true;
    }

    private RuntimeException failureFor(ShortLink shortLink) {
        return switch (shortLink.getStatus()) {
            case BROKEN -> new BrokenLinkException("short link is broken: " + shortLink.getShortCode());
            case EXHAUSTED -> exhausted(shortLink);
            case ACTIVE -> new IllegalStateException("active status must be resolvable");
        };
    }
}
