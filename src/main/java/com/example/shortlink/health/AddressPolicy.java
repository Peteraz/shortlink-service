package com.example.shortlink.health;

import java.net.URI;

@FunctionalInterface
public interface AddressPolicy {

    void validate(URI uri);
}
