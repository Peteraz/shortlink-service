package com.example.shortlink.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShortLinkControllerTest {

    private final MockMvc mockMvc;

    @Autowired
    ShortLinkControllerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void shouldCreateAndQueryNormalShortLink() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "HTTPS://EXAMPLE.COM/article/1001?from=wechat",
                                  "channel": " wechat "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value(hasLength(6)))
                .andExpect(jsonPath("$.data.channel").value("wechat"))
                .andExpect(jsonPath("$.data.type").value("NORMAL"))
                .andExpect(jsonPath("$.data.originalUrls[0]")
                        .value("https://example.com/article/1001?from=wechat"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(get("/api/v1/short-links/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value(shortCode));
    }

    @Test
    void shouldDefaultBlankChannelAndRejectInvalidShortCode() throws Exception {
        mockMvc.perform(post("/api/v1/short-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/article/1002\",\"channel\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("default"));

        mockMvc.perform(get("/api/v1/short-links/abc1234"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHORT_CODE"));
    }

    @Test
    void shouldRejectInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/short-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://example.com/file\",\"channel\":\"wechat\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }
}
