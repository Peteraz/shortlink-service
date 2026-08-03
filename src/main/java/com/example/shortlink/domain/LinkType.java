package com.example.shortlink.domain;

public enum LinkType {
    /**
     * 只包含一个原始长链接的普通短链。
     */
    NORMAL,
    /**
     * 包含多个候选长链接并按次随机解析的盲盒短链。
     */
    BLIND_BOX
}
