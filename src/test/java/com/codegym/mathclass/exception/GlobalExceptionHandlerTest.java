package com.codegym.mathclass.exception;

import com.codegym.mathclass.ai.strategy.parser.exception.AiParsingException;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleResourceNotFoundException trả về 404 với error message")
    void testHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("User not found");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleResourceNotFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("User not found", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleBadRequestException trả về 400")
    void testHandleBadRequestException() {
        BadRequestException ex = new BadRequestException("Invalid input data");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleBadRequestException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid input data", response.getBody().get("error"));
        assertEquals("Invalid input data", response.getBody().get("message"));
    }

    @Test
    @DisplayName("handlePromptNotFoundException trả về 404")
    void testHandlePromptNotFoundException() {
        PromptNotFoundException ex = new PromptNotFoundException("Prompt not found");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handlePromptNotFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Prompt not found", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleInvalidVariableException trả về 400 và errorCode INVALID_VARIABLE")
    void testHandleInvalidVariableException() {
        InvalidVariableException ex = new InvalidVariableException("Missing {{topic}}");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleInvalidVariableException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_VARIABLE", response.getBody().get("errorCode"));
        assertEquals("Missing {{topic}}", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleAccessDeniedException trả về 403")
    void testHandleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Forbidden action");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleAccessDeniedException(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Forbidden action", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleValidationExceptions trả về 400 và chi tiết field errors")
    void testHandleValidationExceptions() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError = new FieldError("object", "email", "Email không được để trống");
        when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Map<String, String>>> response = exceptionHandler.handleValidationExceptions(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("errors"));
        assertEquals("Email không được để trống", response.getBody().get("errors").get("email"));
    }

    @Test
    @DisplayName("handleTooManyRequestsException trả về 429")
    void testHandleTooManyRequestsException() {
        TooManyRequestsException ex = new TooManyRequestsException("Rate limit exceeded");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleTooManyRequestsException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Rate limit exceeded", response.getBody().get("message"));
    }

    @Test
    @DisplayName("handleInsufficientCreditException trả về 402 và errorCode INSUFFICIENT_CREDITS")
    void testHandleInsufficientCreditException() {
        InsufficientCreditException ex = new InsufficientCreditException("Bạn không đủ credit");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleInsufficientCreditException(ex);

        assertEquals(HttpStatus.PAYMENT_REQUIRED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INSUFFICIENT_CREDITS", response.getBody().get("errorCode"));
        assertEquals("Bạn không đủ credit", response.getBody().get("message"));
    }

    @Test
    @DisplayName("handleNoResourceFoundException trả về 404")
    void testHandleNoResourceFoundException() {
        NoResourceFoundException ex = mock(NoResourceFoundException.class);
        when(ex.getMessage()).thenReturn("No static resource found");

        ResponseEntity<Map<String, String>> response = exceptionHandler.handleNoResourceFoundException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("No static resource found", response.getBody().get("error"));
    }

    @Test
    @DisplayName("handleAiGenerationException trả về đúng HTTP status từ ngoại lệ")
    void testHandleAiGenerationException() {
        AiGenerationException ex = new AiGenerationException(429, "Quota exceeded");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleAiGenerationException(ex);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Quota exceeded", response.getBody().get("message"));
    }

    @Test
    @DisplayName("handleAiParsingException trả về 422 Unprocessable Content")
    void testHandleAiParsingException() {
        AiParsingException ex = new AiParsingException("Failed to parse JSON");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleAiParsingException(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Failed to parse JSON", response.getBody().get("message"));
    }

    @Test
    @DisplayName("handlePropertyReferenceException trả về 400")
    void testHandlePropertyReferenceException() {
        PropertyReferenceException ex = mock(PropertyReferenceException.class);
        when(ex.getPropertyName()).thenReturn("invalidField");
        when(ex.getMessage()).thenReturn("No property 'invalidField' found");

        ResponseEntity<Map<String, String>> response = exceptionHandler.handlePropertyReferenceException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().get("message").contains("invalidField"));
    }

    @Test
    @DisplayName("handleGeneralException trả về 500 khi gặp ngoại lệ bất ngờ")
    void testHandleGeneralException() {
        Exception ex = new RuntimeException("Unexpected database failure");
        ResponseEntity<Map<String, String>> response = exceptionHandler.handleGeneralException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Đã có lỗi hệ thống xảy ra. Vui lòng thử lại sau.", response.getBody().get("message"));
    }
}
