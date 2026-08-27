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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
}
