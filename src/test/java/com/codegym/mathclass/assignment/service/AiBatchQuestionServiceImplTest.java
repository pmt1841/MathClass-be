package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.impl.AiBatchQuestionServiceImpl;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiBatchQuestionServiceImplTest {

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private KeySelectionService keySelectionService;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PromptRenderService promptRenderService;

    @Mock
    private AiProviderStrategyFactory aiProviderStrategyFactory;

    @Mock
    private AiProviderStrategy aiProviderStrategy;

    @InjectMocks
    private AiBatchQuestionServiceImpl aiBatchQuestionService;

    private Provider mockProvider;
    private TaskConfig mockTaskConfig;
    private ApiKey mockApiKey;

    @BeforeEach
    void setUp() {
        mockProvider = Provider.builder()
                .code("GEMINI")
                .name("Gemini")
                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                .status(ProviderStatus.ACTIVE)
                .build();
        mockProvider.setId(1L);

        mockTaskConfig = TaskConfig.builder()
                .task("BATCH_QUESTION_GEN")
                .provider(mockProvider)
                .model("gemini-2.0-flash")
                .enabled(true)
                .maxToken(4096)
                .build();

        mockApiKey = ApiKey.builder()
                .encryptedKey("test-api-key")
                .provider(mockProvider)
                .build();
        mockApiKey.setId(1L);
    }

    @Test
    @DisplayName("Should throw BadRequestException when document content is blank")
    void testBatchGenerate_BlankContent_ThrowsBadRequestException() {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder().build();
        assertThrows(BadRequestException.class, () -> aiBatchQuestionService.batchGenerateQuestions(request, 1L));
    }

    @Test
    @DisplayName("Should successfully batch generate questions from text content")
    void testBatchGenerate_FromText_Success() throws Exception {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình x + 1 = 2. Bài 2: Rút gọn biểu thức.")
                .grade(9)
                .includeExplanation(true)
                .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.empty());
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(mockApiKey);
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        String jsonAiResponse = """
                {
                  "suggestedTitle": "Đề kiểm tra 15 phút Toán 9",
                  "suggestedDescription": "Gồm 2 bài tập đại số",
                  "questions": [
                    {
                      "id": "q1",
                      "title": "Bài 1: Giải phương trình",
                      "content": "Giải phương trình: $x + 1 = 2$",
                      "explanation": "Ta có: $x = 2 - 1 = 1$.",
                      "difficulty": "NHAN_BIET",
                      "suggestedScore": 5.0
                    },
                    {
                      "id": "q2",
                      "title": "Bài 2: Rút gọn",
                      "content": "Rút gọn: $A = \\\\sqrt{4}$",
                      "explanation": "$A = 2$.",
                      "difficulty": "THONG_HIEU",
                      "suggestedScore": 5.0
                    }
                  ]
                }
                """;

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("test-api-key"), anyString()))
                .thenReturn(new AiExecutionResult(jsonAiResponse, 200));

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(request, 1L);

        assertNotNull(response);
        assertEquals("Đề kiểm tra 15 phút Toán 9", response.getSuggestedTitle());
        assertEquals(2, response.getTotalQuestions());
        assertEquals("Bài 1: Giải phương trình", response.getQuestions().get(0).getTitle());
        assertEquals("Giải phương trình: $x + 1 = 2$", response.getQuestions().get(0).getContent());
    }

    @Test
    @DisplayName("Should successfully batch generate questions from uploaded file")
    void testBatchGenerate_FromFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "de-thi.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "Dummy word content".getBytes()
        );

        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .file(file)
                .grade(10)
                .includeExplanation(true)
                .build();

        when(assignmentService.extractTextFromFile(file)).thenReturn(
                Map.of("content", "Nội dung trích xuất từ file docx", "images", Collections.emptyList())
        );
        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.empty());
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(mockApiKey);
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        String jsonAiResponse = """
                {
                  "suggestedTitle": "Đề thi khảo sát",
                  "suggestedDescription": "Tài liệu bóc tách từ file Word",
                  "questions": [
                    {
                      "id": "q1",
                      "title": "Bài 1",
                      "content": "Tính $S = 1 + 2$",
                      "explanation": "$S = 3$",
                      "difficulty": "NHAN_BIET",
                      "suggestedScore": 10.0
                    }
                  ]
                }
                """;

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("test-api-key"), anyString()))
                .thenReturn(new AiExecutionResult(jsonAiResponse, 150));

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(request, 1L);

        assertNotNull(response);
        assertEquals("Đề thi khảo sát", response.getSuggestedTitle());
        assertEquals(1, response.getTotalQuestions());
    }

    @Test
    @DisplayName("Should reserve credits before call and settle credits on success for non-admin user")
    void testCreditLifecycle_ReserveAndSettle_Success() throws Exception {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình 2x = 4")
                .build();

        com.codegym.mathclass.user.entity.User teacherUser = new com.codegym.mathclass.user.entity.User();
        teacherUser.setId(1L);
        teacherUser.setRole(com.codegym.mathclass.user.entity.Role.TEACHER);

        com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig creditConfig =
                com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig.builder()
                        .task("BATCH_QUESTION_GEN")
                        .enabled(true)
                        .costPerCall(2)
                        .tokensPerCredit(1000)
                        .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.of(creditConfig));
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacherUser));
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(mockApiKey);
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        String jsonAiResponse = """
                {
                  "suggestedTitle": "Đề kiểm tra",
                  "questions": [
                    { "id": "q1", "title": "Bài 1", "content": "$2x = 4$" }
                  ]
                }
                """;

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("test-api-key"), anyString()))
                .thenReturn(new AiExecutionResult(jsonAiResponse, 100));

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(request, 1L);

        assertNotNull(response);
        verify(aiCreditService).reserve(1L, "BATCH_QUESTION_GEN", 2);
        verify(aiCreditService).settle(eq(1L), eq("BATCH_QUESTION_GEN"), eq(2), anyInt());
    }

    @Test
    @DisplayName("Should refund reserved credits when AI execution throws exception")
    void testCreditLifecycle_RefundOnException() throws Exception {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình 2x = 4")
                .build();

        com.codegym.mathclass.user.entity.User teacherUser = new com.codegym.mathclass.user.entity.User();
        teacherUser.setId(1L);
        teacherUser.setRole(com.codegym.mathclass.user.entity.Role.TEACHER);

        com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig creditConfig =
                com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig.builder()
                        .task("BATCH_QUESTION_GEN")
                        .enabled(true)
                        .costPerCall(2)
                        .tokensPerCredit(1000)
                        .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.of(creditConfig));
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacherUser));
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(mockApiKey);
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("test-api-key"), anyString()))
                .thenThrow(new RuntimeException("Connection timeout"));

        assertThrows(AiGenerationException.class, () -> aiBatchQuestionService.batchGenerateQuestions(request, 1L));

        verify(aiCreditService).reserve(1L, "BATCH_QUESTION_GEN", 2);
        verify(aiCreditService, atLeastOnce()).refund(1L, "BATCH_QUESTION_GEN", 2);
        verify(aiCreditService, never()).settle(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should NOT reserve or charge credits for ADMIN user")
    void testCreditLifecycle_FreeForAdmin() throws Exception {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình 2x = 4")
                .build();

        com.codegym.mathclass.user.entity.User adminUser = new com.codegym.mathclass.user.entity.User();
        adminUser.setId(99L);
        adminUser.setRole(com.codegym.mathclass.user.entity.Role.ADMIN);

        com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig creditConfig =
                com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig.builder()
                        .task("BATCH_QUESTION_GEN")
                        .enabled(true)
                        .costPerCall(2)
                        .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.of(creditConfig));
        when(userRepository.findById(99L)).thenReturn(Optional.of(adminUser));
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(mockApiKey);
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        String jsonAiResponse = """
                {
                  "suggestedTitle": "Đề kiểm tra",
                  "questions": [
                    { "id": "q1", "title": "Bài 1", "content": "$2x = 4$" }
                  ]
                }
                """;

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("test-api-key"), anyString()))
                .thenReturn(new AiExecutionResult(jsonAiResponse, 200));

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(request, 99L);

        assertNotNull(response);
        verify(aiCreditService, never()).reserve(any(), any(), anyInt());
        verify(aiCreditService, never()).settle(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should handle 401 and 429 failover and succeed with next available key")
    void testFailover_401And429_RetriesNextKey() throws Exception {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình 2x = 4")
                .build();

        ApiKey key1 = ApiKey.builder().encryptedKey("key-1").provider(mockProvider).build();
        key1.setId(101L);
        ApiKey key2 = ApiKey.builder().encryptedKey("key-2").provider(mockProvider).build();
        key2.setId(102L);
        ApiKey key3 = ApiKey.builder().encryptedKey("key-3").provider(mockProvider).build();
        key3.setId(103L);

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(mockTaskConfig));
        when(aiCreditService.getCreditConfig("BATCH_QUESTION_GEN")).thenReturn(Optional.empty());
        when(promptRenderService.renderPrompt(any())).thenReturn(
                RenderPromptResponse.builder().renderedPrompt("Rendered Prompt Content").build()
        );
        when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)).thenReturn(aiProviderStrategy);

        when(keySelectionService.selectKeyForProvider(mockProvider)).thenReturn(key1, key2, key3);

        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("key-1"), anyString()))
                .thenThrow(new AiGenerationException(401, "Invalid API key"));
        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("key-2"), anyString()))
                .thenThrow(new AiGenerationException(429, "Rate limit exceeded"));

        String jsonAiResponse = """
                {
                  "suggestedTitle": "Đề kiểm tra",
                  "questions": [
                    { "id": "q1", "title": "Bài 1", "content": "$2x = 4$" }
                  ]
                }
                """;
        when(aiProviderStrategy.executePrompt(eq(mockProvider), eq(mockTaskConfig), eq("key-3"), anyString()))
                .thenReturn(new AiExecutionResult(jsonAiResponse, 200));

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(request, 1L);

        assertNotNull(response);
        assertEquals("Đề kiểm tra", response.getSuggestedTitle());
        verify(keySelectionService).markKeyAsInactive(101L);
        verify(keySelectionService).cooldownKey(102L, 300);
    }

    @Test
    @DisplayName("Should throw AiGenerationException 503 when TaskConfig is disabled")
    void testTaskConfigDisabled_ThrowsAiGenerationException503() {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình")
                .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.empty());
        when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.empty());

        AiGenerationException ex = assertThrows(
                AiGenerationException.class,
                () -> aiBatchQuestionService.batchGenerateQuestions(request, 1L)
        );

        assertEquals(503, ex.getStatusCode());
    }

    @Test
    @DisplayName("Should throw AiGenerationException 503 when Provider is inactive")
    void testProviderInactive_ThrowsAiGenerationException503() {
        BatchGenerateQuestionsRequest request = BatchGenerateQuestionsRequest.builder()
                .textContent("Bài 1: Giải phương trình")
                .build();

        Provider inactiveProvider = Provider.builder()
                .code("GEMINI")
                .status(ProviderStatus.INACTIVE)
                .build();

        TaskConfig taskConfigWithInactiveProvider = TaskConfig.builder()
                .task("BATCH_QUESTION_GEN")
                .provider(inactiveProvider)
                .enabled(true)
                .build();

        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(taskConfigWithInactiveProvider));

        AiGenerationException ex = assertThrows(
                AiGenerationException.class,
                () -> aiBatchQuestionService.batchGenerateQuestions(request, 1L)
        );

        assertEquals(503, ex.getStatusCode());
    }
}
