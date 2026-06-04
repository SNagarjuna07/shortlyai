package com.shortlyai.url.shortening.common.exception;

public class DuplicateSlugException extends RuntimeException {

    public DuplicateSlugException(String message) {
        super(message);
    }
}
