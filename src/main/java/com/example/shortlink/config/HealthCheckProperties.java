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
    private static final int DEFAULT_BATCH_TIMEOUT_MILLIS = 15_000;
    private static final int DEFAULT_URL_PROBE_POOL_SIZE = 16;
    private static final int DEFAULT_URL_PROBE_QUEUE_CAPACITY = 64;
    private static final int DEFAULT_DNS_RESOLVER_POOL_SIZE = 4;
    private static final int DEFAULT_DNS_RESOLVER_QUEUE_CAPACITY = 64;

    /**
     * 建立 TCP 连接允许使用的最长时间，仍受整个 URL 探测截止时间约束。
     */
    @Min(1)
    @Max(60_000)
    private int connectTimeoutMillis = DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /**
     * 单个原始 URL 的绝对探测截止时间，覆盖 DNS、全部候选 IP、TCP/TLS、HEAD 及必要时的 GET。
     */
    @Min(1)
    @Max(120_000)
    private int requestTimeoutMillis = DEFAULT_REQUEST_TIMEOUT_MILLIS;

    /**
     * 同步批量请求的绝对截止时间，避免客户端长时间占用 Web 请求线程。
     */
    @Min(1)
    @Max(300_000)
    private int batchTimeoutMillis = DEFAULT_BATCH_TIMEOUT_MILLIS;

    /**
     * 全局原始 URL 探测线程数；网络 I/O 并发由该值统一约束。
     */
    @Min(1)
    private int urlProbePoolSize = DEFAULT_URL_PROBE_POOL_SIZE;

    /**
     * 原始 URL 探测线程池的有界等待队列容量。
     */
    @Min(1)
    private int urlProbeQueueCapacity = DEFAULT_URL_PROBE_QUEUE_CAPACITY;

    /**
     * DNS 解析使用独立线程池，避免探测线程相互等待造成饥饿。
     */
    @Min(1)
    private int dnsResolverPoolSize = DEFAULT_DNS_RESOLVER_POOL_SIZE;

    /**
     * DNS 解析线程池的有界等待队列容量。
     */
    @Min(1)
    private int dnsResolverQueueCapacity = DEFAULT_DNS_RESOLVER_QUEUE_CAPACITY;

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

    public int getBatchTimeoutMillis() {
        return batchTimeoutMillis;
    }

    public void setBatchTimeoutMillis(int batchTimeoutMillis) {
        this.batchTimeoutMillis = batchTimeoutMillis;
    }

    public int getUrlProbePoolSize() {
        return urlProbePoolSize;
    }

    public void setUrlProbePoolSize(int urlProbePoolSize) {
        this.urlProbePoolSize = urlProbePoolSize;
    }

    public int getUrlProbeQueueCapacity() {
        return urlProbeQueueCapacity;
    }

    public void setUrlProbeQueueCapacity(int urlProbeQueueCapacity) {
        this.urlProbeQueueCapacity = urlProbeQueueCapacity;
    }

    public int getDnsResolverPoolSize() {
        return dnsResolverPoolSize;
    }

    public void setDnsResolverPoolSize(int dnsResolverPoolSize) {
        this.dnsResolverPoolSize = dnsResolverPoolSize;
    }

    public int getDnsResolverQueueCapacity() {
        return dnsResolverQueueCapacity;
    }

    public void setDnsResolverQueueCapacity(int dnsResolverQueueCapacity) {
        this.dnsResolverQueueCapacity = dnsResolverQueueCapacity;
    }
}
