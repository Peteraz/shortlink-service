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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
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

        assertTrue(ok.isReachable());
        assertEquals(200, ok.getHttpStatus());
        assertTrue(redirect.isReachable());
        assertEquals(302, redirect.getHttpStatus());
    }

    @Test
    void shouldTreatFourHundredAndFiveHundredStatusesAsUnreachable() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        assertFalse(checker.check(url("/not-found")).isReachable());
        assertEquals(404, checker.check(url("/not-found")).getHttpStatus());
        assertFalse(checker.check(url("/server-error")).isReachable());
        assertEquals(500, checker.check(url("/server-error")).getHttpStatus());
    }

    @Test
    void shouldFallbackToGetWhenHeadIsNotAllowed() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        UrlHealthResult result = checker.check(url("/head405"));

        assertTrue(result.isReachable());
        assertEquals(200, result.getHttpStatus());
        assertEquals(1, head405GetCount.get());
    }

    @Test
    void shouldReturnUnreachableWhenRequestTimesOut() {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 100);

        UrlHealthResult result = checker.check(url("/timeout"));

        assertFalse(result.isReachable());
        assertTrue(result.getMessage().contains("timed out"));
    }

    @Test
    void shouldReturnUnreachableWhenTlsHandshakeTimesOut() throws Exception {
        try (ServerSocket stalledTlsServer = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try (Socket ignored = stalledTlsServer.accept()) {
                    timeoutRelease.await(5, TimeUnit.SECONDS);
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            serverThread.start();

            try {
                DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 100);

                UrlHealthResult result = checker.check(
                        "https://127.0.0.1:" + stalledTlsServer.getLocalPort() + "/");

                assertFalse(result.isReachable());
                assertTrue(result.getMessage().contains("timed out"));
            } finally {
                timeoutRelease.countDown();
                serverThread.join(1_000);
            }

            assertFalse(serverThread.isAlive());
        }
    }

    @Test
    void shouldReturnUnreachableWhenConnectionFails() throws IOException {
        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);

        UrlHealthResult result = checker.check("http://127.0.0.1:" + unusedPort + "/");

        assertFalse(result.isReachable());
    }

    @Test
    void shouldBlockLocalhostWithDefaultAddressPolicy() {
        DefaultLinkHealthChecker checker = checker(new DefaultAddressPolicy(), 500);

        UrlHealthResult result = checker.check("http://localhost:" + port + "/ok");

        assertFalse(result.isReachable());
        assertTrue(result.getMessage().contains("SSRF security policy"));
    }

    @Test
    void shouldTryNextResolvedAddressWhenTheFirstAddressFails() throws Exception {
        DefaultLinkHealthChecker checker = checker(new AllowAllAddressPolicy(), 500);
        String targetUrl = url("/ok");

        UrlHealthResult result = checker.checkResolvedAddresses(
                targetUrl,
                URI.create(targetUrl),
                new InetAddress[]{
                        InetAddress.getByName("127.0.0.2"),
                        InetAddress.getByName("127.0.0.1")
                },
                System.nanoTime());

        assertTrue(result.isReachable());
        assertEquals(200, result.getHttpStatus());
    }

    @Test
    void shouldFormatIpv6HostHeaderWithOnePairOfBrackets() {
        assertEquals(
                "[2001:db8::1]:8443",
                DefaultLinkHealthChecker.hostHeader(URI.create("http://[2001:db8::1]:8443/")));
    }

    @Test
    void shouldPercentEncodeNonAsciiRequestTarget() {
        assertEquals(
                "/%E4%BD%A0%E5%A5%BD?q=%E4%B8%96%E7%95%8C",
                DefaultLinkHealthChecker.requestTarget(URI.create("http://example.com/\u4f60\u597d?q=\u4e16\u754c")));
    }

    private DefaultLinkHealthChecker checker(AddressPolicy addressPolicy, int requestTimeoutMillis) {
        HealthCheckProperties properties = new HealthCheckProperties();
        properties.setConnectTimeoutMillis(200);
        properties.setRequestTimeoutMillis(requestTimeoutMillis);
        return new DefaultLinkHealthChecker(addressPolicy, properties);
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
            // 本地 HttpServer 端点仅在测试中按策略放行。
        }

        @Override
        public void validateResolvedAddresses(InetAddress... addresses) {
            // 本地回环地址仅在测试中按策略放行。
        }
    }
}
