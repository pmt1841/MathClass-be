package com.codegym.mathclass.exception;

public class HintLimitExceededException extends BadRequestException {
    public HintLimitExceededException(String message) {
        super(message);
    }
}
