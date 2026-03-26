package com.example.soliditycompiler.exception;

public class WrapperGenerationException extends RuntimeException {

    public WrapperGenerationException(String message) {
        super(message);
    }

    public WrapperGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

