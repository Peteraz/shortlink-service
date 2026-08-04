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
     * 普通短链和盲盒短链都只有 ACTIVE 状态允许进入解析流程。
     * 盲盒剩余次数的并发扣减由 tryConsume 继续负责最终判断。
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
        if (shortLink.getStatus() == LinkStatus.EXHAUSTED) {
            throw new BlindBoxExhaustedException("exhausted short link cannot be marked broken: " + shortLink.getShortCode());
        }
    }

    public BlindBoxExhaustedException exhausted(ShortLink shortLink) {
        return new BlindBoxExhaustedException("blind-box short link has no remaining times: " + shortLink.getShortCode());
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
