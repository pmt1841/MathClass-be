package com.codegym.mathclass.exception;

/**
 * Ngoại lệ báo hiệu người dùng đã hết credit AI và cần mua thêm.
 * Xử lý tập trung tại {@code GlobalExceptionHandler} -> HTTP 402 Payment Required
 * kèm errorCode = "INSUFFICIENT_CREDITS".
 */
public class InsufficientCreditException extends RuntimeException {

    public InsufficientCreditException(String message) {
        super(message);
    }
}
