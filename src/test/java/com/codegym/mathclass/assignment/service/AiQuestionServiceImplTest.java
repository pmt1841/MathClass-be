package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.codegym.mathclass.assignment.dto.request.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.impl.AiQuestionServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import com.codegym.mathclass.assignment.dto.response.AiGeneratedQuestionResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class AiQuestionServiceImplTest {

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

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        private AiQuestionServiceImpl aiQuestionService;

        private GenerateQuestionRequest requestDTO;
        private Provider provider;
        private ApiKey apiKey;

        @BeforeEach
        void setUp() {
                provider = Provider.builder()
                                .code("GEMINI")
                                .name("Google Gemini")
                                .protocol(ProviderProtocol.GOOGLE_GEMINI_COMPATIBLE)
                                .status(ProviderStatus.ACTIVE)
                                .build();

                requestDTO = GenerateQuestionRequest.builder()
                                .prompt("Cho tam giác ABC vuông tại A...")
                                .grade(9)
                                .difficulty("THONG_HIEU")
                                .topic("Hình học 9")
                                .includeCanvasDiagram(true)
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
                TaskConfig disabledConfig = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(false)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(disabledConfig));

                AiGenerationException ex = assertThrows(AiGenerationException.class,
                                () -> aiQuestionService.generateQuestion(requestDTO));

                assertEquals(503, ex.getStatusCode());
                assertTrue(ex.getMessage().contains("Tính năng AI hiện đang được bảo trì"));
        }

        @Test
        @DisplayName("Should throw AiGenerationException (503) when Provider is INACTIVE")
        void testGenerateQuestion_InactiveProvider_ThrowsException() {
                Provider inactiveProvider = Provider.builder()
                                .code("GEMINI")
                                .status(ProviderStatus.INACTIVE)
                                .build();

                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(inactiveProvider)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));

                AiGenerationException ex = assertThrows(AiGenerationException.class,
                                () -> aiQuestionService.generateQuestion(requestDTO));

                assertEquals(503, ex.getStatusCode());
                assertTrue(ex.getMessage().contains("Tính năng AI hiện đang được bảo trì"));
        }

        @Test
        @DisplayName("Should retain canvasData when includeCanvasDiagram is true even without drawing keywords in prompt")
        void testGenerateQuestion_IncludeCanvasDiagramTrue_RetainsCanvasData() throws Exception {
                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(provider)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));
                when(keySelectionService.selectKeyForProvider(provider)).thenReturn(apiKey);
                when(promptRenderService.renderPrompt(any())).thenReturn(RenderPromptResponse.builder().renderedPrompt("Rendered System Prompt").build());
                when(aiProviderStrategyFactory.getStrategy(any())).thenReturn(aiProviderStrategy);

                String mockAiJson = """
                        {
                            "title": "Bài toán tam giác",
                            "content": "Cho tam giác $ABC$ vuông tại $A$...",
                            "explanation": "",
                            "canvasData": {
                                "width": 500,
                                "height": 400,
                                "elements": [
                                    { "type": "point", "id": "A", "x": 0.0, "y": 0.0, "label": "A" }
                                ]
                            }
                        }
                        """;

                when(aiProviderStrategy.executePrompt(any(), any(), any(), any()))
                                .thenReturn(new AiExecutionResult(mockAiJson, 100));

                AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(requestDTO, 1L);

                assertNotNull(response);
                assertNotNull(response.getCanvasData());
                assertEquals(1, response.getCanvasData().getElements().size());
        }

        @Test
        @DisplayName("Should remove canvasData when includeCanvasDiagram is false even if AI returned canvasData")
        void testGenerateQuestion_IncludeCanvasDiagramFalse_RemovesCanvasData() throws Exception {
                GenerateQuestionRequest noCanvasReq = GenerateQuestionRequest.builder()
                                .prompt("Cho hình chữ nhật ABCD, hãy vẽ đồ thị...")
                                .grade(9)
                                .difficulty("THONG_HIEU")
                                .topic("Hình học 9")
                                .includeCanvasDiagram(false)
                                .build();

                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(provider)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));
                when(keySelectionService.selectKeyForProvider(provider)).thenReturn(apiKey);
                when(promptRenderService.renderPrompt(any())).thenReturn(RenderPromptResponse.builder().renderedPrompt("Rendered System Prompt").build());
                when(aiProviderStrategyFactory.getStrategy(any())).thenReturn(aiProviderStrategy);

                String mockAiJson = """
                        {
                            "title": "Bài toán hình chữ nhật",
                            "content": "Cho hình chữ nhật $ABCD$...",
                            "explanation": "",
                            "canvasData": {
                                "width": 500,
                                "height": 400,
                                "elements": [
                                    { "type": "point", "id": "A", "x": 0.0, "y": 0.0, "label": "A" }
                                ]
                            }
                        }
                        """;

                when(aiProviderStrategy.executePrompt(any(), any(), any(), any()))
                                .thenReturn(new AiExecutionResult(mockAiJson, 100));

                AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(noCanvasReq, 1L);

                assertNotNull(response);
                assertNull(response.getCanvasData());
        }

        @Test
        @DisplayName("Should retain explanation when includeExplanation is true")
        void testGenerateQuestion_IncludeExplanationTrue_RetainsExplanation() throws Exception {
                GenerateQuestionRequest reqWithExp = GenerateQuestionRequest.builder()
                                .prompt("Cho tam giác ABC...")
                                .grade(9)
                                .difficulty("THONG_HIEU")
                                .topic("Hình học 9")
                                .includeCanvasDiagram(false)
                                .includeExplanation(true)
                                .build();

                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(provider)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));
                when(keySelectionService.selectKeyForProvider(provider)).thenReturn(apiKey);
                when(promptRenderService.renderPrompt(any())).thenReturn(RenderPromptResponse.builder().renderedPrompt("Rendered System Prompt").build());
                when(aiProviderStrategyFactory.getStrategy(any())).thenReturn(aiProviderStrategy);

                String mockAiJson = """
                        {
                            "title": "Bài toán tam giác",
                            "content": "Cho tam giác $ABC$...",
                            "explanation": "Bước 1: Tính đường cao...",
                            "canvasData": null
                        }
                        """;

                when(aiProviderStrategy.executePrompt(any(), any(), any(), any()))
                                .thenReturn(new AiExecutionResult(mockAiJson, 100));

                AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(reqWithExp, 1L);

                assertNotNull(response);
                assertEquals("Bước 1: Tính đường cao...", response.getExplanation());
        }

        @Test
        @DisplayName("Should clear explanation when includeExplanation is false even if prompt mentions explanation")
        void testGenerateQuestion_IncludeExplanationFalse_ClearsExplanation() throws Exception {
                GenerateQuestionRequest reqNoExp = GenerateQuestionRequest.builder()
                                .prompt("Cho tam giác ABC kèm lời giải chi tiết...")
                                .grade(9)
                                .difficulty("THONG_HIEU")
                                .topic("Hình học 9")
                                .includeCanvasDiagram(false)
                                .includeExplanation(false)
                                .build();

                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(provider)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));
                when(keySelectionService.selectKeyForProvider(provider)).thenReturn(apiKey);
                when(promptRenderService.renderPrompt(any())).thenReturn(RenderPromptResponse.builder().renderedPrompt("Rendered System Prompt").build());
                when(aiProviderStrategyFactory.getStrategy(any())).thenReturn(aiProviderStrategy);

                String mockAiJson = """
                        {
                            "title": "Bài toán tam giác",
                            "content": "Cho tam giác $ABC$...",
                            "explanation": "Bước 1: Tính đường cao...",
                            "canvasData": null
                        }
                        """;

                when(aiProviderStrategy.executePrompt(any(), any(), any(), any()))
                                .thenReturn(new AiExecutionResult(mockAiJson, 100));

                AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(reqNoExp, 1L);

                assertNotNull(response);
                assertEquals("", response.getExplanation());
        }

        @Test
        @DisplayName("generateQuestion - Trừ credit và quyết toán dựa trên prompt tokens và completion tokens")
        void testGenerateQuestion_WithCredit_ReservesAndSettlesCorrectly() throws Exception {
                GenerateQuestionRequest req = GenerateQuestionRequest.builder()
                                .prompt("Cho hình chữ nhật ABCD có AB = 4, BC = 3. Tính độ dài đường chéo BD.")
                                .grade(8)
                                .difficulty("THONG_HIEU")
                                .topic("Hình học 8")
                                .includeCanvasDiagram(false)
                                .includeExplanation(true)
                                .build();

                User teacher = User.builder().role(Role.TEACHER).build();
                teacher.setId(10L);
                when(userRepository.findById(10L))
                                .thenReturn(Optional.of(teacher));

                AiCreditConfig creditConfig = AiCreditConfig.builder()
                                .enabled(true)
                                .costPerCall(3)
                                .tokensPerCredit(1000)
                                .build();
                when(aiCreditService.getCreditConfig("QUESTION_GEN"))
                                .thenReturn(Optional.of(creditConfig));

                TaskConfig config = TaskConfig.builder()
                                .task("QUESTION_GEN")
                                .enabled(true)
                                .provider(provider)
                                .maxToken(2048)
                                .build();

                when(taskConfigRepository.findByTask("QUESTION_GEN")).thenReturn(Optional.of(config));
                when(keySelectionService.selectKeyForProvider(provider)).thenReturn(apiKey);
                when(promptRenderService.renderPrompt(any())).thenReturn(RenderPromptResponse.builder().renderedPrompt("Rendered System Prompt").build());
                when(aiProviderStrategyFactory.getStrategy(any())).thenReturn(aiProviderStrategy);

                String mockAiJson = """
                        {
                            "title": "Độ dài đường chéo",
                            "content": "Cho hình chữ nhật ABCD...",
                            "explanation": "BD = sqrt(3^2 + 4^2) = 5",
                            "canvasData": null
                        }
                        """;
                when(aiProviderStrategy.executePrompt(any(), any(), any(), any()))
                                .thenReturn(new AiExecutionResult(mockAiJson, 120));

                AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(req, 10L);

                assertNotNull(response);
                assertEquals("BD = sqrt(3^2 + 4^2) = 5", response.getExplanation());
                verify(aiCreditService).reserve(eq(10L), eq("QUESTION_GEN"), anyInt());
                verify(aiCreditService).settle(eq(10L), eq("QUESTION_GEN"), anyInt(), anyInt());
        }
}
