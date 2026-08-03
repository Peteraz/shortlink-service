package com.example.shortlink.service;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import com.example.shortlink.dto.request.CreateBlindBoxLinkRequest;
import com.example.shortlink.dto.request.CreateNormalLinkRequest;
import com.example.shortlink.dto.request.ShortLinkQuery;
import com.example.shortlink.dto.response.PageResponse;
import com.example.shortlink.dto.response.ShortLinkResponse;
import com.example.shortlink.exception.BlindBoxExhaustedException;
import com.example.shortlink.exception.BrokenLinkException;
import com.example.shortlink.exception.BusinessException;
import com.example.shortlink.generator.ShortCodeGenerator;
import com.example.shortlink.mapper.ShortLinkMapper;
import com.example.shortlink.repository.InMemoryShortLinkRepository;
import com.example.shortlink.validator.ChannelNormalizer;
import com.example.shortlink.validator.ShortCodeValidator;
import com.example.shortlink.validator.UrlValidator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortLinkServicePhaseFourTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-03T02:00:00Z"), ZoneOffset.UTC);
    private static final List<String> BLIND_BOX_URLS = List.of(
            "https://example.com/one",
            "https://example.com/two");

    @Test
    void shouldMarkActiveLinkBrokenIdempotentlyAndKeepItQueryable() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"), FIXED_CLOCK);
        String shortCode = service.createNormalLink(
                new CreateNormalLinkRequest("https://example.com/active", "wechat"))
                .shortCode();

        ShortLinkResponse first = service.markBroken(shortCode, "  运营人员主动下线  ");
        ShortLinkResponse second = service.markBroken(shortCode, "another reason");

        assertEquals(LinkStatus.BROKEN, first.status());
        assertEquals("运营人员主动下线", first.brokenReason());
        assertEquals(first.brokenReason(), second.brokenReason());
        assertEquals(LinkStatus.BROKEN, service.getByShortCode(shortCode).status());
        assertThrows(BrokenLinkException.class, () -> service.resolve(shortCode));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldNotMarkExhaustedBlindBoxBroken() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        ShortLinkService service = createService(repository, new SequenceGenerator("abc123"), FIXED_CLOCK);
        String shortCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(BLIND_BOX_URLS, "wechat", 1)).shortCode();

        service.resolve(shortCode);

        assertThrows(BlindBoxExhaustedException.class,
                () -> service.markBroken(shortCode, "too late"));
        assertEquals(LinkStatus.EXHAUSTED, service.getByShortCode(shortCode).status());
    }

    @Test
    void shouldFilterSortAndPaginateShortLinks() {
        InMemoryShortLinkRepository repository = new InMemoryShortLinkRepository();
        IncrementingClock clock = new IncrementingClock();
        ShortLinkService service = createService(
                repository,
                new SequenceGenerator("abc123", "def456", "ghi789"),
                clock);

        String firstCode = service.createNormalLink(
                new CreateNormalLinkRequest("https://example.com/first", "alpha")).shortCode();
        String secondCode = service.createNormalLink(
                new CreateNormalLinkRequest("https://example.com/second", "beta")).shortCode();
        String thirdCode = service.createBlindBoxLink(
                new CreateBlindBoxLinkRequest(BLIND_BOX_URLS, "alpha", 2)).shortCode();

        PageResponse<ShortLinkResponse> firstPage = service.query(
                new ShortLinkQuery(null, null, null, null, 0, 2));
        PageResponse<ShortLinkResponse> secondPage = service.query(
                new ShortLinkQuery(null, null, null, null, 1, 2));

        assertEquals(3, firstPage.totalElements());
        assertEquals(2, firstPage.totalPages());
        assertEquals(List.of(thirdCode, secondCode), firstPage.content().stream()
                .map(ShortLinkResponse::shortCode)
                .toList());
        assertEquals(List.of(firstCode), secondPage.content().stream()
                .map(ShortLinkResponse::shortCode)
                .toList());

        PageResponse<ShortLinkResponse> channelPage = service.query(
                new ShortLinkQuery(null, "alpha", null, null, 0, 20));
        assertEquals(2, channelPage.totalElements());

        PageResponse<ShortLinkResponse> typePage = service.query(
                new ShortLinkQuery(null, "alpha", null, LinkType.BLIND_BOX, 0, 20));
        assertEquals(1, typePage.totalElements());
        assertEquals(thirdCode, typePage.content().getFirst().shortCode());

        service.markBroken(firstCode, "offline");
        PageResponse<ShortLinkResponse> statusPage = service.query(
                new ShortLinkQuery(null, null, LinkStatus.BROKEN, null, 0, 20));
        assertEquals(1, statusPage.totalElements());
        assertEquals(firstCode, statusPage.content().getFirst().shortCode());
    }

    @Test
    void shouldRejectInvalidPageSize() {
        ShortLinkService service = createService(
                new InMemoryShortLinkRepository(), new SequenceGenerator("abc123"), FIXED_CLOCK);

        assertThrows(BusinessException.class,
                () -> service.query(new ShortLinkQuery(null, null, null, null, 0, 101)));
    }

    private static ShortLinkService createService(
            InMemoryShortLinkRepository repository,
            ShortCodeGenerator generator,
            Clock clock) {
        ShortLinkProperties properties = new ShortLinkProperties();
        return new ShortLinkServiceImpl(
                repository,
                generator,
                new UrlValidator(),
                new ChannelNormalizer(),
                new NormalLinkBusinessKeyFactory(),
                new ShortLinkMapper(properties),
                new ShortCodeValidator(properties),
                clock);
    }

    private static final class SequenceGenerator implements ShortCodeGenerator {

        private final List<String> codes;
        private final AtomicInteger index = new AtomicInteger();

        private SequenceGenerator(String... codes) {
            this.codes = List.of(codes);
        }

        @Override
        public String generate() {
            return codes.get(Math.min(index.getAndIncrement(), codes.size() - 1));
        }
    }

    private static final class IncrementingClock extends Clock {

        private final AtomicLong sequence = new AtomicLong();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.parse("2026-08-03T02:00:00Z")
                    .plusMillis(sequence.getAndIncrement());
        }
    }
}
