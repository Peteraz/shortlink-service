package com.example.shortlink.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * 业务响应码，成功时为 0。
     */
    private final String code;
    /**
     * 面向调用方的响应消息。
     */
    private final String message;
    /**
     * 响应数据。
     */
    private final T data;
    /**
     * 响应生成时间。
     */
    private final LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("0", "success", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>("0", message, data, LocalDateTime.now());
    }
}
