package com.codegym.mathclass.exception;

public class InvalidSubmissionStateException extends BadRequestException {
    public InvalidSubmissionStateException(String message) {
        super(message);
    }
}
