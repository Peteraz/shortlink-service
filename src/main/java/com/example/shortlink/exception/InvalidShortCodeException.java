package com.example.shortlink.exception;

public class InvalidShortCodeException extends BusinessException {

    public InvalidShortCodeException(String message) {
        super("INVALID_SHORT_CODE", message);
    }
}
