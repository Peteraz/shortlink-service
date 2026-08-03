package com.example.shortlink.generator;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62ShortCodeGeneratorTest {

    private final Base62ShortCodeGenerator generator =
            new Base62ShortCodeGenerator(new SecureRandom(), Base62ShortCodeGenerator.DEFAULT_LENGTH);

    @Test
    void shouldGenerateConfiguredLength() {
        assertEquals(7, generator.generate().length());
    }

    @Test
    void shouldGenerateOnlyBase62Characters() {
        String code = generator.generate();

        assertTrue(code.chars()
                .allMatch(character -> Base62ShortCodeGenerator.BASE62_CHARACTERS.indexOf(character) >= 0));
    }

    @Test
    void shouldHaveAlmostNoDuplicatesForManyGeneratedCodes() {
        int sampleSize = 10_000;
        Set<String> codes = new HashSet<>(sampleSize);

        for (int index = 0; index < sampleSize; index++) {
            codes.add(generator.generate());
        }

        assertTrue(codes.size() >= sampleSize - 1,
                () -> "too many duplicate codes: " + (sampleSize - codes.size()));
    }
}
