package com.example.shortlink.service;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import com.example.shortlink.domain.ShortLink;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.response.ResolveResult;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.exception.BlindBoxDuplicateUrlException;
import com.example.shortlink.exception.BlindBoxExhaustedException;
import com.example.shortlink.exception.BlindBoxUrlInsufficientException;
import com.example.shortlink.exception.BrokenLinkException;
import com.example.shortlink.generator.ShortCodeGenerator;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.repository.InMemoryShortLinkRepository;
import com.example.shortlink.selector.DefaultBlindBoxSelector;
import com.example.shortlink.validator.ChannelNormalizer;
import com.example.shortlink.validator.ShortCodeValidator;
import com.example.shortlink.validator.UrlValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlindBoxLinkServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC);
    private static final List<String> CANDIDATE_URLS = List.of(
            "https://example.com/one",
            "https://example.com/two",
            "https://example.com/three");

    @Test
    void shouldCreateBlindBoxLink() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"));

        ShortLinkResponse response = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", 10));

        assertEquals(LinkType.BLIND_BOX, response.type());
        assertEquals(CANDIDATE_URLS, response.originalUrls());
        assertEquals(10, response.remainingTimes());
        assertEquals(LinkStatus.ACTIVE, response.status());
        assertEquals("wechat", response.channel());
    }

    @Test
    void shouldRejectBlindBoxWithFewerThanTwoUrls() {
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"));

        assertThrows(BlindBoxUrlInsufficientException.class, () -> service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(
                        List.of("https://example.com/one"), "wechat", 10)));
    }

    @Test
    void shouldRejectDuplicateBlindBoxUrls() {
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"));

        assertThrows(BlindBoxDuplicateUrlException.class, () -> service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(
                        List.of("https://example.com/one", "HTTPS://EXAMPLE.COM/one"),
                        "wechat",
                        10)));
    }

    @Test
    void shouldAlwaysResolveToOneOfTheCandidateUrls() {
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"));
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", 100)).shortCode();

        for (int index = 0; index < 100; index++) {
            ResolveResult result = service.resolve(shortCode);
            assertTrue(CANDIDATE_URLS.contains(result.targetUrl()));
        }
    }

    @Test
    void shouldResolveNormalLinkAndIncrementResolveCount() {
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"));
        String shortCode = service.createNormalLink(
                new CreateNormalLinkRequest("HTTPS://EXAMPLE.COM/normal", "wechat"))
                .shortCode();

        ResolveResult result = service.resolve(shortCode);

        assertEquals("https://example.com/normal", result.targetUrl());
        assertEquals(LinkType.NORMAL, result.type());
        assertEquals("wechat", result.channel());
        assertEquals(1, result.resolveCount());
        assertEquals(LinkStatus.ACTIVE, result.status());
        assertNull(result.remainingTimes());
    }

    @Test
    void shouldRejectBrokenLinkBeforeResolving() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"));
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", 3)).shortCode();
        repository.findByShortCode(shortCode).orElseThrow().markBroken("test");

        assertThrows(BrokenLinkException.class, () -> service.resolve(shortCode));
    }

    @Test
    void shouldProduceAReasonablyUniformDistribution() {
        int resolveTimes = 30_000;
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"));
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", resolveTimes)).shortCode();
        Map<String, Integer> counts = new HashMap<>();

        for (int index = 0; index < resolveTimes; index++) {
            String targetUrl = service.resolve(shortCode).targetUrl();
            counts.merge(targetUrl, 1, Integer::sum);
        }

        assertEquals(3, counts.size());
        for (String candidateUrl : CANDIDATE_URLS) {
            double ratio = counts.getOrDefault(candidateUrl, 0) / (double) resolveTimes;
            assertTrue(ratio >= 0.28 && ratio <= 0.39,
                    () -> "unexpected ratio for " + candidateUrl + ": " + ratio);
        }
    }

    @Test
    void shouldExhaustAfterConfiguredNumberOfResolutions() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"));
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", 3)).shortCode();

        service.resolve(shortCode);
        service.resolve(shortCode);
        ResolveResult lastResult = service.resolve(shortCode);

        assertEquals(0, lastResult.remainingTimes());
        assertEquals(LinkStatus.EXHAUSTED, lastResult.status());
        assertEquals(3, lastResult.resolveCount());
        assertThrows(BlindBoxExhaustedException.class, () -> service.resolve(shortCode));

        ShortLink storedLink = repository.findByShortCode(shortCode).orElseThrow();
        assertEquals(0, storedLink.getRemainingTimes().get());
        assertEquals(3, storedLink.getResolveCount().get());
    }

    @Test
    void shouldNotOverConsumeUnderConcurrency() throws Exception {
        int validTimes = 100;
        int taskCount = 1_000;
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"));
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(CANDIDATE_URLS, "wechat", validTimes)).shortCode();
        ShortLink storedLink = repository.findByShortCode(shortCode).orElseThrow();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger minimumObservedRemaining = new AtomicInteger(validTimes);
        ExecutorService executor = Executors.newFixedThreadPool(100);

        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                tasks.add(() -> {
                    start.await();
                    try {
                        service.resolve(shortCode);
                        return true;
                    } catch (BlindBoxExhaustedException exception) {
                        return false;
                    } finally {
                        minimumObservedRemaining.accumulateAndGet(
                                storedLink.getRemainingTimes().get(), Math::min);
                    }
                });
            }

            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> task : tasks) {
                futures.add(executor.submit(task));
            }
            start.countDown();

            int successCount = 0;
            int failureCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }

            assertEquals(validTimes, successCount);
            assertEquals(taskCount - validTimes, failureCount);
            assertEquals(0, storedLink.getRemainingTimes().get());
            assertEquals(0, minimumObservedRemaining.get());
            assertEquals(validTimes, storedLink.getResolveCount().get());
            assertEquals(LinkStatus.EXHAUSTED, storedLink.getStatus());
        } finally {
            executor.shutdownNow();
        }
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
                new DefaultBlindBoxSelector(),
                FIXED_CLOCK);
    }

    private static final class SequenceGenerator implements ShortCodeGenerator {

        private final String code;

        private SequenceGenerator(String code) {
            this.code = code;
        }

        @Override
        public String generate() {
            return code;
        }
    }
}
