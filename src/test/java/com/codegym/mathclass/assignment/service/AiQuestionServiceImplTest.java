package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.service.impl.AiQuestionServiceImpl;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AiQuestionServiceImplTest {

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private KeySelectionService keySelectionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiQuestionServiceImpl aiQuestionService;

    private GenerateQuestionRequest requestDTO;
    private Provider provider;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        requestDTO = GenerateQuestionRequest.builder()
                .prompt("Cho tam giác ABC nhọn nội tiếp đường tròn (O; R)...")
                .grade(9)
                .difficulty("THONG_HIEU")
                .topic("Hình học 9")
                .includeCanvasDiagram(true)
                .build();

        provider = Provider.builder()
                .code("GEMINI")
                .name("Google Gemini")
                .status(ProviderStatus.ACTIVE)
                .build();

        apiKey = ApiKey.builder()
                .encryptedKey("test-api-key")
                .provider(provider)
                .build();
        apiKey.setId(1L);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when prompt is blank")
    void testGenerateQuestion_BlankPrompt_ThrowsException() {
        GenerateQuestionRequest invalidReq = GenerateQuestionRequest.builder()
                .prompt("  ")
                .build();

        assertThrows(IllegalArgumentException.class, () -> aiQuestionService.generateQuestion(invalidReq));
    }

    @Test
    @DisplayName("Should throw AiGenerationException (503) when TASK_QUESTION_GEN is disabled")
    void testGenerateQuestion_DisabledTaskConfig_ThrowsException() {
        com.codegym.mathclass.aiconfig.entity.TaskConfig disabledConfig = com.codegym.mathclass.aiconfig.entity.TaskConfig.builder()
                .task("QUESTION_GEN")
                .enabled(false)
                .build();

        when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(disabledConfig));

        com.codegym.mathclass.assignment.exception.AiGenerationException ex = 
                assertThrows(com.codegym.mathclass.assignment.exception.AiGenerationException.class, 
                        () -> aiQuestionService.generateQuestion(requestDTO));

        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Tính năng sinh đề chưa được cấu hình hoặc đã bị tắt"));
    }

    @Test
    @DisplayName("Should throw AiGenerationException (503) when Provider is INACTIVE")
    void testGenerateQuestion_InactiveProvider_ThrowsException() {
        Provider inactiveProvider = Provider.builder()
                .code("GEMINI")
                .status(ProviderStatus.INACTIVE)
                .build();

        com.codegym.mathclass.aiconfig.entity.TaskConfig config = com.codegym.mathclass.aiconfig.entity.TaskConfig.builder()
                .task("QUESTION_GEN")
                .enabled(true)
                .provider(inactiveProvider)
                .build();

        when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));

        com.codegym.mathclass.assignment.exception.AiGenerationException ex = 
                assertThrows(com.codegym.mathclass.assignment.exception.AiGenerationException.class, 
                        () -> aiQuestionService.generateQuestion(requestDTO));

        assertEquals(503, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("Provider cấu hình cho việc sinh đề không tồn tại hoặc đã bị tắt"));
    }
}

