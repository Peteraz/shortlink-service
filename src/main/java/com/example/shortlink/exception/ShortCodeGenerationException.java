package com.example.shortlink.exception;

public class ShortCodeGenerationException extends BusinessException {

    public ShortCodeGenerationException(String message) {
        super("SHORT_CODE_GENERATION_FAILED", message);
    }

    public ShortCodeGenerationException(String message, Throwable cause) {
        super("SHORT_CODE_GENERATION_FAILED", message, cause);
    }
}
