package com.example.shortlink.mapper;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.response.ShortLinkResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortLinkMapperTest {

    @Test
    void shouldWaitForStateLockBeforeCreatingResponseSnapshot() throws Exception {
        ShortLink shortLink = ShortLink.blindBox(
                "abc1234",
                List.of("https://example.com/one", "https://example.com/two"),
                "wechat",
                LocalDateTime.of(2026, 8, 4, 10, 0),
                3);
        ShortLinkMapper mapper = new ShortLinkMapper(new ShortLinkProperties());
        CountDownLatch mappingStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<ShortLinkResponse> responseFuture = shortLink.withStateLock(() -> {
                Future<ShortLinkResponse> future = executor.submit(() -> {
                    mappingStarted.countDown();
                    return mapper.toResponse(shortLink);
                });
                await(mappingStarted);
                assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
                return future;
            });

            ShortLinkResponse response = responseFuture.get(1, TimeUnit.SECONDS);
            assertEquals(0, response.getResolveCount());
            assertEquals(3, response.getRemainingTimes());
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test thread interrupted", exception);
        }
    }
}
