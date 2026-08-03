package com.example.shortlink.validator;

import com.example.shortlink.config.ShortLinkProperties;
import com.example.shortlink.exception.InvalidShortCodeException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortCodeValidatorTest {

    @ParameterizedTest
    @CsvSource({"6, abc123", "7, abc1234", "8, abc12345"})
    void shouldAcceptOnlyTheConfiguredLength(int length, String shortCode) {
        ShortLinkProperties properties = properties(length);

        assertDoesNotThrow(() -> new ShortCodeValidator(properties).validate(shortCode));
    }

    @ParameterizedTest
    @CsvSource({"6, abc1234", "6, abc12!", "7, abc123", "7, abc12345", "8, abc123", "8, abc123456"})
    void shouldRejectWrongLengthOrNonBase62Characters(int length, String shortCode) {
        ShortLinkProperties properties = properties(length);

        assertThrows(InvalidShortCodeException.class,
                () -> new ShortCodeValidator(properties).validate(shortCode));
    }

    private static ShortLinkProperties properties(int codeLength) {
        ShortLinkProperties properties = new ShortLinkProperties();
        properties.setCodeLength(codeLength);
        return properties;
    }
}
