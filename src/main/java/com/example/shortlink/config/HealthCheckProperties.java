package com.example.shortlink.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "short-link.health-check")
public class HealthCheckProperties {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2_000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MILLIS = 3_000;
    private static final int DEFAULT_CORE_POOL_SIZE = 4;
    private static final int DEFAULT_MAX_POOL_SIZE = 8;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    @Min(1)
    @Max(60_000)
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

    @Min(1)
    @Max(120_000)
    private int requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS;

    @Min(1)
    private int corePoolSize = DEFAULT_CORE_POOL_SIZE;

    @Min(1)
    private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

    @Min(1)
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    @Min(1)
    private int keepAliveSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeoutMillis(int requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }
}
