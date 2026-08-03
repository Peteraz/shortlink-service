package com.example.shortlink.exception;

public class BlindBoxDuplicateUrlException extends BusinessException {

    public BlindBoxDuplicateUrlException(String message) {
        super("BLIND_BOX_DUPLICATE_URL", message);
    }
}
