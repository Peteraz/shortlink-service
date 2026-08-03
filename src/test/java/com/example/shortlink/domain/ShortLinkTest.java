package com.example.shortlink.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortLinkTest {

    @Test
    void shouldUseDefaultChannelAndProtectOriginalUrls() {
        List<String> urls = new ArrayList<>(List.of("https://example.com/one"));
        ShortLink link = ShortLink.normal(
                "abc1234",
                urls.getFirst(),
                " ",
                LocalDateTime.of(2026, 8, 3, 10, 0));

        urls.set(0, "https://example.com/changed");

        assertEquals("default", link.getChannel());
        assertEquals(List.of("https://example.com/one"), link.getOriginalUrls());
        assertThrows(UnsupportedOperationException.class,
                () -> link.getOriginalUrls().add("https://example.com/two"));
        assertEquals(0, link.getResolveCount().get());
        assertEquals(LinkStatus.ACTIVE, link.getStatus());
    }

    @Test
    void shouldRequirePositiveBlindBoxRemainingTimes() {
        assertThrows(IllegalArgumentException.class, () -> ShortLink.blindBox(
                "abc1234",
                List.of("https://example.com/one", "https://example.com/two"),
                null,
                LocalDateTime.now(),
                0));
    }

    @Test
    void shouldKeepBlindBoxRemainingTimes() {
        ShortLink link = ShortLink.blindBox(
                "abc1234",
                List.of("https://example.com/one", "https://example.com/two"),
                null,
                LocalDateTime.now(),
                5);

        assertTrue(link.getRemainingTimes() != null);
        assertEquals(5, link.getRemainingTimes().get());
        assertEquals(LinkType.BLIND_BOX, link.getType());
    }
}
