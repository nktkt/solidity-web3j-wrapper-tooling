package com.example.soliditycompiler.exception;

public class CompilationFailedException extends RuntimeException {

    public CompilationFailedException(String message) {
        super(message);
    }

    public CompilationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

