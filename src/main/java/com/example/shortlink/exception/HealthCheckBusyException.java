package com.example.shortlink.exception;

/** 健康检测资源已饱和，调用方可稍后重试。 */
public class HealthCheckBusyException extends BusinessException {

    public HealthCheckBusyException(String message) {
        super("HEALTH_CHECK_BUSY", message);
    }
}
