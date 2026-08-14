package com.example.shortlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckExecutorConfigurationTest {

    @Test
    void shouldUseFixedUrlProbeConcurrencyAndFailFastRejectionPolicy() throws InterruptedException {
        HealthCheckProperties properties = new HealthCheckProperties();
        ThreadPoolTaskExecutor executor = new ShortLinkConfiguration().urlProbeExecutor(properties);
        CountDownLatch started = new CountDownLatch(properties.getUrlProbePoolSize());
        CountDownLatch release = new CountDownLatch(1);

        try {
            for (int index = 0; index < properties.getUrlProbePoolSize(); index++) {
                executor.execute(() -> {
                    started.countDown();
                    await(release);
                });
            }

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.getCorePoolSize()).isEqualTo(properties.getUrlProbePoolSize());
            assertThat(executor.getMaxPoolSize()).isEqualTo(properties.getUrlProbePoolSize());
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        } finally {
            release.countDown();
            executor.destroy();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
