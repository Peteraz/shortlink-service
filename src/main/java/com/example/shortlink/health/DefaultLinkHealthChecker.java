package com.example.shortlink.health;

import com.example.shortlink.config.HealthCheckProperties;
import com.example.shortlink.dto.response.UrlHealthResult;
import org.springframework.stereotype.Component;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 健康检测器。
 * <p>
 * SSRF / DNS rebinding 防护的核心流程：解析一次、校验、固定地址直连。
 * <ol>
 *   <li>对 host 只做一次 DNS 解析；</li>
 *   <li>通过 {@link AddressPolicy} 校验解析出的每一个地址；</li>
 *   <li>依次向通过校验的 IP 建立连接，不再进行第二次 DNS 解析。</li>
 * </ol>
 * 这样攻击者无法利用“校验与连接之间 DNS 记录被替换”（TOCTOU）把请求
 * 重定向到内网地址。JDK 21 的 HttpClient 不支持自定义 DNS 解析钩子，
 * 因此这里基于原生 Socket/SSLSocket 实现极简的 HTTP 探测，
 * 仅读取响应状态行，不下载响应体、不跟随重定向。
 */
@Component
public class DefaultLinkHealthChecker implements LinkHealthChecker {

    /**
     * HTTP 可达状态码的下界。
     */
    private static final int HTTP_OK_MIN = 200;
    /**
     * HTTP 可达状态码的上界，不包含 400。
     */
    private static final int HTTP_REDIRECT_MAX = 400;
    /**
     * HEAD 不被目标服务支持时返回的状态码。
     */
    private static final int METHOD_NOT_ALLOWED = 405;
    /**
     * 默认 HTTP 端口。
     */
    private static final int DEFAULT_HTTP_PORT = 80;
    /**
     * 默认 HTTPS 端口。
     */
    private static final int DEFAULT_HTTPS_PORT = 443;
    /**
     * 状态行最大长度，防止对端返回无边界数据耗尽内存。
     */
    private static final int MAX_STATUS_LINE_BYTES = 8 * 1024;
    /**
     * 纯数字与点组成的 host 视为 IPv4 字面量。
     */
    private static final Pattern IPV4_LITERAL = Pattern.compile("[0-9.]+");

    /**
     * 请求前执行 SSRF 地址安全校验的策略。
     */
    private final AddressPolicy addressPolicy;
    /**
     * 建立 TCP 连接的超时时间。
     */
    private final int connectTimeoutMillis;
    /**
     * TLS 握手与读取响应状态行的超时时间。
     */
    private final int requestTimeoutMillis;

    public DefaultLinkHealthChecker(AddressPolicy addressPolicy, HealthCheckProperties properties) {
        this.addressPolicy = addressPolicy;
        this.connectTimeoutMillis = properties.getConnectTimeoutMillis();
        this.requestTimeoutMillis = properties.getRequestTimeoutMillis();
    }

    /**
     * 先完成 SSRF 校验并固定目标地址，再执行 HEAD；网络异常统一转换为不可达结果。
     */
    @Override
    public UrlHealthResult check(String url) {
        long startedAt = System.nanoTime();
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException exception) {
            return result(url, false, null, "invalid URL", startedAt);
        }

        // DNS rebinding 防护关键：只解析一次 DNS，校验全部地址，
        // 随后直接连接通过校验的地址，期间不再发生第二次解析。
        InetAddress[] addresses;
        try {
            addressPolicy.validate(uri);
            addresses = resolveAll(uri);
            addressPolicy.validateResolvedAddresses(addresses);
        } catch (AddressPolicyViolationException exception) {
            return result(url, false, null, exception.getMessage(), startedAt);
        } catch (RuntimeException exception) {
            // 策略实现自身的缺陷不能导致请求被放行，统一按拒绝处理。
            return result(url, false, null, "request blocked by SSRF security policy", startedAt);
        }

        return checkResolvedAddresses(url, uri, addresses, startedAt);
    }

    /**
     * 按 DNS 解析顺序尝试所有已校验地址。任一地址可达即视为 URL 可达，避免某个
     * 不可用的 IPv4/IPv6 地址导致健康链接被误判为断链。
     */
    UrlHealthResult checkResolvedAddresses(String url, URI uri, InetAddress[] addresses, long startedAt) {
        UrlHealthResult firstFailure = null;
        for (InetAddress address : addresses) {
            UrlHealthResult attempt = checkAddress(url, uri, address, startedAt);
            if (attempt.isReachable()) {
                return attempt;
            }
            if (firstFailure == null) {
                firstFailure = attempt;
            }
        }
        return firstFailure;
    }

    private UrlHealthResult checkAddress(String url, URI uri, InetAddress target, long startedAt) {
        try {
            int statusCode = executeRequest(uri, target, "HEAD");
            if (statusCode == METHOD_NOT_ALLOWED) {
                // HEAD 不被支持时回退到 GET，仍然只读取状态行，不下载响应体。
                statusCode = executeRequest(uri, target, "GET");
            }
            return result(
                    url,
                    isReachable(statusCode),
                    statusCode,
                    messageForStatus(statusCode),
                    startedAt);
        } catch (SocketTimeoutException exception) {
            return result(url, false, null, "request timed out", startedAt);
        } catch (ConnectException exception) {
            return result(url, false, null, "connection failed", startedAt);
        } catch (IOException exception) {
            return result(url, false, null, "request failed", startedAt);
        }
    }

    /**
     * 一次性解析 host 对应的全部 IP。解析失败按不可达处理，
     * 不向外抛出异常，也不泄露服务器内部网络信息。
     */
    private InetAddress[] resolveAll(URI uri) {
        // IPv6 字面量 host 在 URI 中以方括号包裹，解析前需要去掉。
        String host = uri.getHost();
        String lookupHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        try {
            return InetAddress.getAllByName(lookupHost);
        } catch (UnknownHostException exception) {
            throw new AddressPolicyViolationException("DNS resolution failed");
        }
    }

    /**
     * 向已校验的固定地址发起一次 HTTP 请求，只读取并返回响应状态码。
     * 请求头中保留原始 Host；HTTPS 使用原始域名完成 SNI 与证书校验，
     * 因此即使连接地址是固定 IP，TLS 语义与直接访问原域名一致。
     */
    private int executeRequest(URI uri, InetAddress target, String method) throws IOException {
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() >= 0 ? uri.getPort() : (https ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
        try (Socket socket = createConnectedSocket(uri, target, port, https)) {
            writeRequest(socket, uri, method);
            return readStatusCode(socket);
        }
    }

    /**
     * 建立到固定地址的连接。HTTPS 时先创建未连接的 SSLSocket，
     * 以便在握手前设置 SNI 与主机名校验参数。
     */
    private Socket createConnectedSocket(URI uri, InetAddress target, int port, boolean https) throws IOException {
        if (!https) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(target, port), connectTimeoutMillis);
                socket.setSoTimeout(requestTimeoutMillis);
                return socket;
            } catch (IOException | RuntimeException exception) {
                closeAfterFailure(socket, exception);
                throw exception;
            }
        }

        SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        try {
            SSLParameters parameters = socket.getSSLParameters();
            String host = uri.getHost();
            if (!isIpLiteral(host)) {
                // RFC 6066 禁止在 SNI 中携带 IP 字面量，仅域名需要设置 SNI。
                parameters.setServerNames(List.of(new SNIHostName(host)));
            }
            // 启用 HTTPS 主机名校验，确保证书与原始域名匹配。
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            // 连接地址固定为通过校验的 IP，但 TLS 握手仍按原始域名进行 SNI 与证书校验。
            socket.connect(new InetSocketAddress(target, port), connectTimeoutMillis);
            // SO_TIMEOUT 同样覆盖 TLS 握手，防止对端接受 TCP 后一直不返回握手数据。
            socket.setSoTimeout(requestTimeoutMillis);
            socket.startHandshake();
            return socket;
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(socket, exception);
            throw exception;
        }
    }

    private void closeAfterFailure(Socket socket, Exception failure) {
        try {
            socket.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    /**
     * 判断 host 是否为 IP 字面量（IPv4 或 IPv6）。域名不会包含冒号，
     * 也不会仅由数字和点组成。
     */
    private boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || IPV4_LITERAL.matcher(host).matches();
    }

    /**
     * 写出最小化的 HTTP 请求头。Connection: close 告知对端响应后断开，
     * 健康检测无需复用连接。
     */
    private void writeRequest(Socket socket, URI uri, String method) throws IOException {
        String request = method + " " + requestTarget(uri) + " HTTP/1.1\r\n"
                + "Host: " + hostHeader(uri) + "\r\n"
                + "User-Agent: short-link-health-check\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream outputStream = socket.getOutputStream();
        outputStream.write(request.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }

    static String hostHeader(URI uri) {
        String host = uri.getHost();
        String unbracketedHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        StringBuilder hostHeader = new StringBuilder(
                unbracketedHost.indexOf(':') >= 0 ? "[" + unbracketedHost + "]" : unbracketedHost);
        if (uri.getPort() >= 0) {
            hostHeader.append(':').append(uri.getPort());
        }
        return hostHeader.toString();
    }

    static String requestTarget(URI uri) {
        URI asciiUri = URI.create(uri.toASCIIString());
        String path = asciiUri.getRawPath() == null || asciiUri.getRawPath().isEmpty() ? "/" : asciiUri.getRawPath();
        return asciiUri.getRawQuery() == null ? path : path + "?" + asciiUri.getRawQuery();
    }

    /**
     * 只解析响应状态行（如 "HTTP/1.1 200 OK"），不读取响应体。
     */
    private int readStatusCode(Socket socket) throws IOException {
        InputStream inputStream = socket.getInputStream();
        StringBuilder statusLine = new StringBuilder();
        int current;
        while ((current = inputStream.read()) != -1) {
            if (current == '\n') {
                break;
            }
            if (statusLine.length() >= MAX_STATUS_LINE_BYTES) {
                throw new IOException("status line too long");
            }
            if (current != '\r') {
                statusLine.append((char) current);
            }
        }
        String[] parts = statusLine.toString().split(" ");
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("malformed status line");
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            throw new IOException("malformed status line", exception);
        }
    }

    private boolean isReachable(int statusCode) {
        return statusCode >= HTTP_OK_MIN && statusCode < HTTP_REDIRECT_MAX;
    }

    private String messageForStatus(int statusCode) {
        return isReachable(statusCode)
                ? "HTTP status " + statusCode
                : "HTTP status " + statusCode + " is not reachable";
    }

    private UrlHealthResult result(String url, boolean reachable, Integer httpStatus, String message, long startedAt) {
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return new UrlHealthResult(url, reachable, httpStatus, message, elapsedMillis);
    }
}
