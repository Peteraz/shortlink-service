package com.example.shortlink.repository;

import com.example.shortlink.domain.ShortLink;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryShortLinkRepositoryTest {

    private final InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();

    @Test
    void shouldSaveOnlyWhenShortCodeIsAbsent() {
        ShortLink shortLink = normalLink("abc1234");

        assertTrue(repository.saveIfAbsent("abc1234", shortLink));
        assertFalse(repository.saveIfAbsent("abc1234", normalLink("abc1234")));
    }

    @Test
    void shouldFindSavedShortLink() {
        ShortLink shortLink = normalLink("abc1234");
        repository.saveIfAbsent("abc1234", shortLink);

        assertEquals(shortLink, repository.findByShortCode("abc1234").orElseThrow());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldComputeNormalBusinessKeyOnlyOnceUnderConcurrency() throws Exception {
        int taskCount = 32;
        AtomicInteger mappingCalls = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);

        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                tasks.add(() -> repository.computeNormalCodeIfAbsent(
                        "https://example.com/article|wechat",
                        key -> {
                            mappingCalls.incrementAndGet();
                            return "abc1234";
                        }));
            }

            List<Future<String>> futures = executor.invokeAll(tasks);
            for (Future<String> future : futures) {
                assertEquals("abc1234", get(future));
            }
            assertEquals(1, mappingCalls.get());
            assertEquals("abc1234", repository
                    .findNormalCodeByBusinessKey("https://example.com/article|wechat")
                    .orElse(null));
        } finally {
            executor.shutdownNow();
        }
    }

    private static String get(Future<String> future) throws ExecutionException, InterruptedException {
        return future.get();
    }

    private static ShortLink normalLink(String shortCode) {
        ShortLink link = ShortLink.normal(
                shortCode,
                "https://example.com/article",
                "wechat",
                LocalDateTime.of(2026, 8, 3, 10, 0));
        assertNotNull(link);
        return link;
    }
}
