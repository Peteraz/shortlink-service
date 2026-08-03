package com.example.shortlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ShortLinkConfiguration {

    @Bean
    public Clock shortLinkClock() {
        return Clock.systemDefaultZone();
    }
}
