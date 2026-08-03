package com.example.shortlink.service;

import org.springframework.stereotype.Component;

@Component
public class NormalLinkBusinessKeyFactory {

    public String create(String normalizedUrl, String normalizedChannel) {
        return normalizedUrl + "|" + normalizedChannel;
    }
}
