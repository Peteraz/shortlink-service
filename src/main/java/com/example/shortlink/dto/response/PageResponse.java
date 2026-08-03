package com.example.shortlink.dto.response;

import java.util.List;
import java.util.Objects;

import lombok.Getter;

@Getter
public class PageResponse<T> {

    /**
     * 当前页数据，保存为不可变副本。
     */
    private final List<T> content;
    /**
     * 从 0 开始的当前页码。
     */
    private final int page;
    /**
     * 当前页大小。
     */
    private final int size;
    /**
     * 过滤后的总元素数量。
     */
    private final long totalElements;
    /**
     * 按当前页大小计算出的总页数。
     */
    private final int totalPages;

    public PageResponse(List<T> content, int page, int size, long totalElements, int totalPages) {
        this.content = List.copyOf(Objects.requireNonNull(content, "content must not be null"));
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

}
