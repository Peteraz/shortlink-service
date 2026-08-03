package com.example.shortlink.exception;

public class BlindBoxExhaustedException extends BusinessException {

    public BlindBoxExhaustedException(String message) {
        super("BLIND_BOX_EXHAUSTED", message);
    }
}
