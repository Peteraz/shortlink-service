package com.example.shortlink.config;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ShortLinkConfiguration {

    @Bean
    public Clock shortLinkClock() {
        return Clock.systemDefaultZone();
    }

    @Bean(name = "healthCheckHttpClient")
    public HttpClient healthCheckHttpClient(HealthCheckProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean(name = "healthCheckExecutor", destroyMethod = "destroy")
    public ThreadPoolTaskExecutor healthCheckExecutor(HealthCheckProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setThreadNamePrefix("short-link-health-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
