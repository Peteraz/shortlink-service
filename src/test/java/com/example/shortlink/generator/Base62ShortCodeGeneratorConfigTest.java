package com.example.shortlink.generator;

import com.example.shortlink.config.ShortLinkProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Base62ShortCodeGeneratorConfigTest {

    @ParameterizedTest
    @ValueSource(ints = {7, 8})
    void shouldUseTheConfiguredLength(int configuredLength) {
        ShortLinkProperties properties = new ShortLinkProperties();
        properties.setCodeLength(configuredLength);
        Base62ShortCodeGenerator generator = new Base62ShortCodeGenerator(
                new SecureRandom(), properties.getCodeLength());

        assertEquals(configuredLength, generator.generate().length());
    }
}
