package com.example.shortlink.exception;

public class BrokenLinkException extends BusinessException {

    public BrokenLinkException(String message) {
        super("BROKEN_LINK", message);
    }
}
