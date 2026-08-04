package com.example.shortlink.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    void shouldReturnMethodNotAllowedForUnsupportedMethod() throws Exception {
        mockMvc.perform(get("/api/v1/short-links/normal"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void shouldReturnUnsupportedMediaTypeForUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("originalUrl=https://example.com/article/1001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void shouldCreateAndQueryNormalShortLink() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrl": "HTTPS://EXAMPLE.COM/article/1001?from=wechat",
                                  "channel": " wechat "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value(hasLength(7)))
                .andExpect(jsonPath("$.data.channel").value("wechat"))
                .andExpect(jsonPath("$.data.type").value("NORMAL"))
                .andExpect(jsonPath("$.data.originalUrls[0]")
                        .value("https://example.com/article/1001?from=wechat"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(get("/api/v1/short-links/query/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shortCode").value(shortCode))
                .andExpect(jsonPath("$.data.shortUrl").value("http://localhost:8090/s/" + shortCode));
    }

    @Test
    void shouldCreateBlindBoxShortLink() throws Exception {
        mockMvc.perform(post("/api/v1/short-links/blind-box")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrls": [
                                    "https://example.com/one",
                                    "https://example.com/two",
                                    "https://example.com/three"
                                  ],
                                  "channel": "wechat",
                                  "validTimes": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("BLIND_BOX"))
                .andExpect(jsonPath("$.data.remainingTimes").value(10))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void shouldDefaultBlankChannelAndRejectInvalidShortCode() throws Exception {
        mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/article/1002\",\"channel\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("default"));

        mockMvc.perform(get("/api/v1/short-links/query/abc123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHORT_CODE"));
    }

    @Test
    void shouldRejectInvalidUrl() throws Exception {
        mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://example.com/file\",\"channel\":\"wechat\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_URL"));
    }

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
    }

    @Test
    void shouldRedirectNormalShortLinkAndIncrementResolveCount() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/redirect-phase4\",\"channel\":\"phase4redirect\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/redirect-phase4"));

        mockMvc.perform(get("/api/v1/short-links/query/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolveCount").value(1));
    }

    @Test
    void shouldResolveBlindBoxThroughDetailAndConsumeOneTime() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/blind-box")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrls": [
                                    "https://example.com/resolve-one",
                                    "https://example.com/resolve-two"
                                  ],
                                  "channel": "phase4resolve",
                                  "validTimes": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(get("/api/v1/short-links/query/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resolveCount").value(0))
                .andExpect(jsonPath("$.data.remainingTimes").value(3));

        mockMvc.perform(get("/api/v1/short-links/resolve/{shortCode}", shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetUrl").isString())
                .andExpect(jsonPath("$.data.resolveCount").value(1))
                .andExpect(jsonPath("$.data.remainingTimes").value(2));
    }

    @Test
    void shouldMarkBrokenIdempotentlyAndReturnGoneWhenRedirecting() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com/broken-phase4\",\"channel\":\"phase4broken\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(patch("/api/v1/short-links/broken/{shortCode}", shortCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  运营人员主动下线  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BROKEN"))
                .andExpect(jsonPath("$.data.brokenReason").value("运营人员主动下线"));

        mockMvc.perform(patch("/api/v1/short-links/broken/{shortCode}", shortCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"another reason\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brokenReason").value("运营人员主动下线"));

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("BROKEN_LINK"));
    }

    @Test
    void shouldReturnNotFoundForUnknownRedirectCode() throws Exception {
        mockMvc.perform(get("/s/{shortCode}", "zzzz99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHORT_LINK_NOT_FOUND"));
    }

    @Test
    void shouldReturnGoneWhenBlindBoxIsExhausted() throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/blind-box")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrls": [
                                    "https://example.com/exhaust-one",
                                    "https://example.com/exhaust-two"
                                  ],
                                  "channel": "phase4exhaust",
                                  "validTimes": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String shortCode = JsonPath.read(response, "$.data.shortCode");

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isFound());

        mockMvc.perform(get("/s/{shortCode}", shortCode))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("BLIND_BOX_EXHAUSTED"));
    }

    @Test
    void shouldFilterAndPaginateShortLinks() throws Exception {
        String channel = "phase4query";
        String first = createNormal("https://example.com/query-first", channel);
        String second = createNormal("https://example.com/query-second", channel);
        createBlindBox(channel);
        mockMvc.perform(patch("/api/v1/short-links/broken/{shortCode}", second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"offline\"}"))
                .andExpect(status().isOk());

        String pageResponse = mockMvc.perform(get("/api/v1/short-links/queryByPage")
                        .param("channel", channel)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstCreatedAt = JsonPath.read(pageResponse, "$.data.content[0].createdAt");
        String secondCreatedAt = JsonPath.read(pageResponse, "$.data.content[1].createdAt");
        assertTrue(LocalDateTime.parse(firstCreatedAt).compareTo(LocalDateTime.parse(secondCreatedAt)) >= 0);

        mockMvc.perform(get("/api/v1/short-links/queryByPage")
                        .param("channel", channel)
                        .param("status", "BROKEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].shortCode").value(second));

        mockMvc.perform(get("/api/v1/short-links/queryByPage")
                        .param("channel", channel)
                        .param("type", "BLIND_BOX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("BLIND_BOX"));
    }

    private String createNormal(String originalUrl, String channel) throws Exception {
        String response = mockMvc.perform(post("/api/v1/short-links/normal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"" + originalUrl + "\",\"channel\":\"" + channel + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.shortCode");
    }

    private void createBlindBox(String channel) throws Exception {
        mockMvc.perform(post("/api/v1/short-links/blind-box")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "originalUrls": [
                                    "https://example.com/query-blind-one",
                                    "https://example.com/query-blind-two"
                                  ],
                                  "channel": "phase4query",
                                  "validTimes": 3
                                }
                                """.replace("phase4query", channel)))
                .andExpect(status().isOk());
    }
}
