package com.example.shortlink.exception;

public class InvalidUrlException extends BusinessException {

    public InvalidUrlException(String message) {
        super("INVALID_URL", message);
    }
}
