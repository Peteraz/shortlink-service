package com.example.shortlink.validator;

import com.example.shortlink.exception.InvalidShortCodeException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShortCodeValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "abc1234", "abc12345"})
    void shouldAcceptAllSupportedLengths(String shortCode) {
        assertDoesNotThrow(() -> new ShortCodeValidator().validate(shortCode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc12", "abc123456", "abc12!", "abc1234!"})
    void shouldRejectUnsupportedLengthOrNonBase62Characters(String shortCode) {
        assertThrows(InvalidShortCodeException.class,
                () -> new ShortCodeValidator().validate(shortCode));
    }
}
