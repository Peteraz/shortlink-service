package com.example.shortlink.health;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Component
public class DefaultAddressPolicy implements AddressPolicy {

    private static final String BLOCKED_MESSAGE = "request blocked by SSRF security policy";

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

        String lookupHost = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(lookupHost);
        } catch (UnknownHostException exception) {
            throw new AddressPolicyViolationException("DNS resolution failed");
        }

        // Resolve every address and reject if any answer is private or local.
        // This fails closed for multi-address DNS responses and avoids relying
        // only on the user-controlled host string.
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
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
                || isIpv4MappedPrivateAddress(address);
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
