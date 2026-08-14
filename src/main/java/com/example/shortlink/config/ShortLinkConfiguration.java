package com.example.shortlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
public class ShortLinkConfiguration {

    @Bean
    public Clock shortLinkClock() {
        return Clock.systemDefaultZone();
    }

    /**
     * 所有原始 URL 共用一个有界探测池，防止“批量任务池再提交子任务”造成线程饥饿。
     * 队列已满时直接拒绝，由接口返回 503，避免 CallerRuns 占住 Web 请求线程。
     */
    @Bean(name = "urlProbeExecutor", destroyMethod = "destroy")
    public ThreadPoolTaskExecutor urlProbeExecutor(HealthCheckProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getUrlProbePoolSize());
        executor.setMaxPoolSize(properties.getUrlProbePoolSize());
        executor.setQueueCapacity(properties.getUrlProbeQueueCapacity());
        executor.setThreadNamePrefix("short-link-url-probe-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /** DNS 解析独立隔离，避免平台 DNS 调用阻塞并耗尽 URL 探测线程。 */
    @Bean(name = "dnsResolverExecutor", destroyMethod = "destroy")
    public ThreadPoolTaskExecutor dnsResolverExecutor(HealthCheckProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDnsResolverPoolSize());
        executor.setMaxPoolSize(properties.getDnsResolverPoolSize());
        executor.setQueueCapacity(properties.getDnsResolverQueueCapacity());
        executor.setThreadNamePrefix("short-link-dns-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    /** 到达绝对截止时间时主动关闭 Socket，使阻塞中的 TLS 或读取及时结束。 */
    @Bean(name = "healthCheckDeadlineScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService healthCheckDeadlineScheduler() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "short-link-health-deadline");
            thread.setDaemon(true);
            return thread;
        });
    }
}
