package com.example.shortlink.exception;

public class BlindBoxUrlInsufficientException extends BusinessException {

    public BlindBoxUrlInsufficientException(String message) {
        super("BLIND_BOX_URL_INSUFFICIENT", message);
    }
}
