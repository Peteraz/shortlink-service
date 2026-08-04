package com.example.shortlink.domain;

public enum LinkStatus {
    /**
     * 可正常解析的状态。
     */
    ACTIVE,
    /**
     * 已主动或自动标记断链的状态。
     */
    BROKEN
}
