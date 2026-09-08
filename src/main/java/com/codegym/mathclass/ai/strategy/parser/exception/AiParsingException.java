package com.codegym.mathclass.ai.strategy.parser.exception;

/**
 * Ngoại lệ Runtime ném ra khi cả 2 lớp bóc tách (Jackson JSON Lớp 1 và Regex Fallback Lớp 2)
 * đều không thể giải mã chuỗi phản hồi thô từ AI (LLM).
 */
public class AiParsingException extends RuntimeException {

    public AiParsingException(String message) {
        super(message);
    }

    public AiParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
