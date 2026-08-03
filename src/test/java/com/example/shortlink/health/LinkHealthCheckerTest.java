package com.example.shortlink.health;

import com.example.shortlink.config.HealthCheckProperties;
import com.example.shortlink.dto.response.UrlHealthResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkHealthCheckerTest {

    private HttpServer server;
    private int port;
    private AtomicInteger head405GetCount;
    private CountDownLatch timeoutRelease;

    @BeforeEach
    void setUp() throws IOException {
        head405GetCount = new AtomicInteger();
        timeoutRelease = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "ok"));
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/ok");
            respond(exchange, 302, "redirect");
        });
        server.createContext("/not-found", exchange -> respond(exchange, 404, "missing"));
        server.createContext("/server-error", exchange -> respond(exchange, 500, "error"));
        server.createContext("/head405", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "head not allowed");
            } else {
                head405GetCount.incrementAndGet();
                respond(exchange, 200, "get fallback");
            }
        });
        server.createContext("/timeout", exchange -> {
            try {
                timeoutRelease.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "released");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        timeoutRelease.countDown();
        server.stop(0);
    }

    @Test
    void shouldTreatTwoHundredAndThreeHundredStatusesAsReachable() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        UrlHealthResult ok = checker.check(url("/ok"));
        UrlHealthResult redirect = checker.check(url("/redirect"));

        assertTrue(ok.reachable());
        assertEquals(200, ok.httpStatus());
        assertTrue(redirect.reachable());
        assertEquals(302, redirect.httpStatus());
    }

    @Test
    void shouldTreatFourHundredAndFiveHundredStatusesAsUnreachable() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        assertFalse(checker.check(url("/not-found")).reachable());
        assertEquals(404, checker.check(url("/not-found")).httpStatus());
        assertFalse(checker.check(url("/server-error")).reachable());
        assertEquals(500, checker.check(url("/server-error")).httpStatus());
    }

    @Test
    void shouldFallbackToGetWhenHeadIsNotAllowed() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        UrlHealthResult result = checker.check(url("/head405"));

        assertTrue(result.reachable());
        assertEquals(200, result.httpStatus());
        assertEquals(1, head405GetCount.get());
    }

    @Test
    void shouldReturnUnreachableWhenRequestTimesOut() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 100);

        UrlHealthResult result = checker.check(url("/timeout"));

        assertFalse(result.reachable());
        assertTrue(result.message().contains("timed out"));
    }

    @Test
    void shouldReturnUnreachableWhenConnectionFails() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        UrlHealthResult result = checker.check("http://127.0.0.1:" + unusedPort + "/");

        assertFalse(result.reachable());
    }

    @Test
    void shouldBlockLocalhostWithDefaultAddressPolicy() {
        DefaultLinkHealthChecker checker = checker(new DefaultAddressPolicy(), 500);

        UrlHealthResult result = checker.check("http://localhost:" + port + "/ok");

        assertFalse(result.reachable());
        assertTrue(result.message().contains("SSRF security policy"));
    }

    private DefaultLinkHealthChecker checker(AddressPolicy addressPolicy, int requestTimeoutMillis) {
        HealthCheckProperties properties = new HealthCheckProperties();
        properties.setConnectTimeoutMillis(200);
        properties.setRequestTimeoutMillis(requestTimeoutMillis);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new DefaultLinkHealthChecker(client, addressPolicy, properties);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        } else {
            exchange.close();
        }
    }

    private static final class AllowAllAddressPolicy implements AddressPolicy {

        @Override
        public void validate(URI uri) {
            // Local HttpServer endpoints are intentionally allowed only in tests.
        }
    }
}
