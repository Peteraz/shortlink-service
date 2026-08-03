package com.example.shortlink.exception;

public class ShortLinkNotFoundException extends BusinessException {

    public ShortLinkNotFoundException(String message) {
        super("SHORT_LINK_NOT_FOUND", message);
    }
}
