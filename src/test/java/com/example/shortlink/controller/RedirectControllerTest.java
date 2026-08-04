package com.example.shortlink.controller;

import com.example.shortlink.domain.LinkStatus;
import com.example.shortlink.domain.LinkType;
import com.example.shortlink.dto.response.ResolveResponse;
import com.example.shortlink.service.ShortLinkService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RedirectControllerTest {

    @Test
    void shouldRedirectFromPublicShortUrl() throws Exception {
        ShortLinkService service = mock(ShortLinkService.class);
        when(service.resolve("Ab12xY7")).thenReturn(new ResolveResponse(
                "Ab12xY7",
                "https://example.com/article/1001",
                LinkType.NORMAL,
                "wechat",
                LocalDateTime.of(2026, 8, 4, 10, 0),
                1,
                null,
                LinkStatus.ACTIVE));

        MockMvc mockMvc = standaloneSetup(new RedirectController(service)).build();

        mockMvc.perform(get("/s/{shortCode}", "Ab12xY7"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/article/1001"));
    }
}
