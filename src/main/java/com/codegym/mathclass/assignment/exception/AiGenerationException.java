package com.codegym.mathclass.assignment.exception;

import lombok.Getter;

@Getter
public class AiGenerationException extends RuntimeException {
    private int statusCode = 400;

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiGenerationException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}

