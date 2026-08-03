package com.example.shortlink.exception;

public class InvalidChannelException extends BusinessException {

    public InvalidChannelException(String message) {
        super("INVALID_CHANNEL", message);
    }
}
