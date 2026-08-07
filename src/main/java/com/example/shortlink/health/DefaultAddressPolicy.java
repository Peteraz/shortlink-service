package com.example.shortlink.health;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/**
 * 默认 SSRF 安全策略：仅允许 HTTP/HTTPS，且目标地址不得为本机或内网地址。
 */
@Component
public class DefaultAddressPolicy implements AddressPolicy {

    /**
     * 对外返回的统一安全策略拒绝消息，避免泄露内部网络信息。
     */
    private static final String BLOCKED_MESSAGE = "request blocked by SSRF security policy";

    /**
     * 校验协议与 host 字符串，不执行 DNS 解析。
     * 只放行 HTTP/HTTPS，并拒绝明显的 localhost 字样；
     * host 指向的具体地址是否安全，由 {@link #validateResolvedAddresses} 在解析后判断。
     */
    @Override
    public void validate(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || host == null
                || host.isBlank()) {
            throw new AddressPolicyViolationException(BLOCKED_MESSAGE);
        }

        if ("localhost".equalsIgnoreCase(host.trim())) {
            throw new AddressPolicyViolationException(BLOCKED_MESSAGE);
        }
    }

    /**
     * 校验 DNS 解析返回的全部地址。只要其中一个地址受限，就拒绝请求；无法确认安全时同样拒绝。
     */
    @Override
    public void validateResolvedAddresses(InetAddress... addresses) {
        if (addresses == null || addresses.length == 0) {
            throw new AddressPolicyViolationException(BLOCKED_MESSAGE);
        }
        // 不能只根据用户传入的 host 判断安全，必须检查 DNS 返回的每个地址。
        // 只要任一地址属于本机或内网地址，就拒绝整个请求。
        for (InetAddress address : addresses) {
            if (address == null || isBlocked(address)) {
                throw new AddressPolicyViolationException(BLOCKED_MESSAGE);
            }
        }
    }

    private boolean isBlocked(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isIpv6UniqueLocalAddress(address)
                || isIpv4MappedPrivateAddress(address);
    }

    private boolean isIpv6UniqueLocalAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (Byte.toUnsignedInt(bytes[0]) & 0xfe) == 0xfc;
    }

    private boolean isIpv4MappedPrivateAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 16
                || bytes[0] != 0
                || bytes[1] != 0
                || bytes[2] != 0
                || bytes[3] != 0
                || bytes[4] != 0
                || bytes[5] != 0
                || bytes[6] != 0
                || bytes[7] != 0
                || bytes[8] != 0
                || bytes[9] != 0
                || bytes[10] != (byte) 0xff
                || bytes[11] != (byte) 0xff) {
            return false;
        }

        int first = Byte.toUnsignedInt(bytes[12]);
        int second = Byte.toUnsignedInt(bytes[13]);
        return first == 10
                || first == 127
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168
                || first == 0;
    }
}
