package com.codegym.mathclass.exception;

import com.codegym.mathclass.assignment.exception.AiGenerationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.HashMap;
import java.util.Map;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, String>> handleBadRequestException(BadRequestException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(PromptNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePromptNotFoundException(PromptNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InvalidVariableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidVariableException(InvalidVariableException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("errorCode", "INVALID_VARIABLE");
        response.put("error", ex.getMessage());
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler({AccessDeniedException.class, org.springframework.security.access.AccessDeniedException.class})
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage() != null ? ex.getMessage() : "Bạn không có quyền thực hiện thao tác này");
        response.put("message", ex.getMessage() != null ? ex.getMessage() : "Bạn không có quyền thực hiện thao tác này");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        Map<String, Map<String, String>> response = new HashMap<>();
        response.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyRequestsException(TooManyRequestsException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    @ExceptionHandler(InsufficientCreditException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientCreditException(InsufficientCreditException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("errorCode", "INSUFFICIENT_CREDITS");
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResourceFoundException(NoResourceFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<Map<String, String>> handleAiGenerationException(AiGenerationException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("message", ex.getMessage());
        response.put("error", ex.getMessage());
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(response);
    }


    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        // Ghi log lỗi để phục vụ chẩn đoán
        ex.printStackTrace();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
