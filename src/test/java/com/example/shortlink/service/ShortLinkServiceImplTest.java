package com.example.shortlink.service;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.generator.Base62ShortCodeGenerator;
import com.example.shortlink.generator.ShortCodeGenerator;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.repository.InMemoryShortLinkRepository;
import com.example.shortlink.validator.ChannelNormalizer;
import com.example.shortlink.validator.ShortCodeValidator;
import com.example.shortlink.validator.UrlValidator;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortLinkServiceImplTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldReturnSameShortLinkForSameUrlAndChannel() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"));
        CreateNormalLinkRequest request = request("https://example.com/article/1001", "wechat");

        ShortLinkResponse first = service.createNormalLink(request);
        ShortLinkResponse second = service.createNormalLink(request);

        assertEquals(first.shortCode(), second.shortCode());
        assertEquals(1, repository.findAll().size());
        assertEquals(1, repository.findNormalCodeByBusinessKey(
                "https://example.com/article/1001|wechat").stream().count());
    }

    @Test
    void shouldCreateDifferentShortLinksForDifferentChannels() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository,
                new SequenceGenerator("abc123", "def456"));

        ShortLinkResponse wechat = service.createNormalLink(
                request("https://example.com/article/1001", "wechat"));
        ShortLinkResponse douyin = service.createNormalLink(
                request("https://example.com/article/1001", "douyin"));

        assertNotEquals(wechat.shortCode(), douyin.shortCode());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void shouldCreateOnlyOneLinkForConcurrentSameBusinessKey() throws Exception {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        SequenceGenerator generator = new SequenceGenerator("abc123");
        ShortLinkService service = createService(repository, generator);
        int taskCount = 100;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(20);

        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                tasks.add(() -> {
                    start.await();
                    return service.createNormalLink(
                            request("https://example.com/article/1001", "wechat"))
                            .shortCode();
                });
            }

            List<Future<String>> futures = new ArrayList<>();
            for (Callable<String> task : tasks) {
                futures.add(executor.submit(task));
            }
            start.countDown();

            for (Future<String> future : futures) {
                assertEquals("abc123", future.get());
            }
            assertEquals(1, repository.findAll().size());
            assertEquals(1, generator.calls());
            assertTrue(repository.findNormalCodeByBusinessKey(
                    "https://example.com/article/1001|wechat").isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRetryWhenGeneratedShortCodeCollides() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        repository.saveIfAbsent("abc123", ShortLink.normal(
                "abc123",
                "https://example.com/existing",
                "wechat",
                LocalDateTime.now(FIXED_CLOCK)));
        ShortLinkService service = createService(repository,
                new SequenceGenerator("abc123", "def456"));

        ShortLinkResponse response = service.createNormalLink(
                request("https://example.com/article/1001", "wechat"));

        assertEquals("def456", response.shortCode());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void shouldUseSixCharactersByDefault() {
        ShortLinkProperties properties = new ShortLinkProperties();
        Base62ShortCodeGenerator generator = new Base62ShortCodeGenerator(
                new SecureRandom(), properties.getCodeLength());

        String code = generator.generate();

        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(character ->
                Base62ShortCodeGenerator.BASE62_CHARACTERS.indexOf(character) >= 0));
    }

    private static ShortLinkService createService(
            InMemoryShortLinkRepository repository,
            ShortCodeGenerator generator) {
        ShortLinkProperties properties = new ShortLinkProperties();
        return new ShortLinkServiceImpl(
                repository,
                generator,
                new UrlValidator(),
                new ChannelNormalizer(),
                new NormalLinkBusinessKeyFactory(),
                new ShortLinkMapper(properties),
                new ShortCodeValidator(properties),
                FIXED_CLOCK);
    }

    private static CreateNormalLinkRequest request(String url, String channel) {
        return new CreateNormalLinkRequest(url, channel);
    }

    private static final class SequenceGenerator implements ShortCodeGenerator {

        private final List<String> codes;
        private final AtomicInteger index = new AtomicInteger();

        private SequenceGenerator(String... codes) {
            this.codes = List.of(codes);
        }

        @Override
        public String generate() {
            int currentIndex = Math.min(index.getAndIncrement(), codes.size() - 1);
            return codes.get(currentIndex);
        }

        private int calls() {
            return index.get();
        }
    }
}
