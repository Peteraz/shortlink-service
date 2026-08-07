package com.example.shortlink.health;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultAddressPolicyTest {

    private final DefaultAddressPolicy policy = new DefaultAddressPolicy();

    @Test
    void shouldBlockIpv6UniqueLocalAddress() throws Exception {
        assertThrows(
                AddressPolicyViolationException.class,
                () -> policy.validateResolvedAddresses(InetAddress.getByName("fc00::1")));
    }
}
