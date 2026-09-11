package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.*;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPromptExecutionServiceImplTest {

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private KeySelectionService keySelectionService;

    @Mock
    private AiProviderStrategyFactory aiProviderStrategyFactory;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiProviderStrategy mockStrategy;

    @InjectMocks
    private AiPromptExecutionServiceImpl aiPromptExecutionService;

    private Provider activeProvider;
    private TaskConfig activeTaskConfig;
    private ApiKey activeApiKey;

    @BeforeEach
    void setUp() {
        activeProvider = Provider.builder()
                .code("OPENAI")
                .name("OpenAI Provider")
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .build();
        activeProvider.setId(1L);

        activeTaskConfig = TaskConfig.builder()
                .task("MATH_HINT")
                .enabled(true)
                .provider(activeProvider)
                .model("gpt-4o")
                .maxToken(2048)
                .temperature(new BigDecimal("0.7"))
                .build();
        activeTaskConfig.setId(10L);

        activeApiKey = ApiKey.builder()
                .name("Main Key")
                .encryptedKey("sk-test-secret-key")
                .status(ApiKeyStatus.ACTIVE)
                .provider(activeProvider)
                .build();
        activeApiKey.setId(100L);
    }

    @Nested
    @DisplayName("executePrompt & executePromptWithResult")
    class ExecutePromptTests {

        @Test
        @DisplayName("Thực thi prompt thành công khi không trừ credit (chargeCredits = false hoặc userId = null)")
        void executePrompt_Success_WithoutCredits() throws Exception {
            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);

            AiExecutionResult expectedResult = new AiExecutionResult("Giải bài toán bước 1: ...", 150);
            when(mockStrategy.executePrompt(activeProvider, activeTaskConfig, "sk-test-secret-key", "Giải bài này"))
                    .thenReturn(expectedResult);

            String response = aiPromptExecutionService.executePrompt("MATH_HINT", "Giải bài này", null);

            assertThat(response).isEqualTo("Giải bài toán bước 1: ...");
            verify(aiCreditService, never()).reserve(any(), any(), anyInt());
            verify(aiCreditService, never()).settle(any(), any(), anyInt(), anyInt());
            verify(aiCreditService, never()).refund(any(), any(), anyInt());
        }

        @Test
        @DisplayName("Thực thi prompt thành công với Reserve-then-Refund và Settle credit cho người dùng thường")
        void executePrompt_Success_WithCredits_NonAdminUser() throws Exception {
            Long userId = 42L;
            User studentUser = User.builder().role(Role.STUDENT).build();
            studentUser.setId(userId);

            AiCreditConfig creditConfig = AiCreditConfig.builder()
                    .task("MATH_HINT")
                    .enabled(true)
                    .costPerCall(2)
                    .tokensPerCredit(500)
                    .build();

            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));
            when(userRepository.findById(userId)).thenReturn(Optional.of(studentUser));
            when(aiCreditService.getCreditConfig("MATH_HINT")).thenReturn(Optional.of(creditConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);

            AiExecutionResult result = new AiExecutionResult("Kết quả AI", 300);
            when(mockStrategy.executePrompt(activeProvider, activeTaskConfig, "sk-test-secret-key", "Prompt"))
                    .thenReturn(result);

            AiExecutionResult actualResult = aiPromptExecutionService.executePromptWithResult("MATH_HINT", "Prompt", userId, true);

            assertThat(actualResult.content()).isEqualTo("Kết quả AI");
            verify(aiCreditService).reserve(eq(userId), eq("MATH_HINT"), anyInt());
            verify(aiCreditService).settle(eq(userId), eq("MATH_HINT"), anyInt(), anyInt());
            verify(aiCreditService, never()).refund(anyLong(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Tài khoản ADMIN được miễn phí credit khi gọi AI")
        void executePrompt_AdminUser_ExemptFromCredits() throws Exception {
            Long adminId = 1L;
            User adminUser = User.builder().role(Role.ADMIN).build();
            adminUser.setId(adminId);

            AiCreditConfig creditConfig = AiCreditConfig.builder()
                    .task("MATH_HINT")
                    .enabled(true)
                    .costPerCall(10)
                    .build();

            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));
            when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
            when(aiCreditService.getCreditConfig("MATH_HINT")).thenReturn(Optional.of(creditConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);
            when(mockStrategy.executePrompt(any(), any(), any(), any()))
                    .thenReturn(new AiExecutionResult("Admin Result", 20));

            AiExecutionResult result = aiPromptExecutionService.executePromptWithResult("MATH_HINT", "Prompt", adminId, true);

            assertThat(result.content()).isEqualTo("Admin Result");
            verify(aiCreditService, never()).reserve(anyLong(), anyString(), anyInt());
        }

        @Test
        @DisplayName("Ném ngoại lệ khi TaskConfig không tồn tại trong cơ sở dữ liệu")
        void executePrompt_TaskConfigNotFound_ThrowsException() {
            when(taskConfigRepository.findByTask("UNKNOWN_TASK")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiPromptExecutionService.executePrompt("UNKNOWN_TASK", "Prompt", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Tính năng AI hiện đang được bảo trì");
        }

        @Test
        @DisplayName("Ném ngoại lệ khi TaskConfig bị vô hiệu hóa (enabled = false)")
        void executePrompt_TaskConfigDisabled_ThrowsException() {
            activeTaskConfig.setEnabled(false);
            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));

            assertThatThrownBy(() -> aiPromptExecutionService.executePrompt("MATH_HINT", "Prompt", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Tính năng AI hiện đang được bảo trì");
        }

        @Test
        @DisplayName("Ném ngoại lệ khi Provider bị null hoặc trạng thái không phải ACTIVE")
        void executePrompt_ProviderInactive_ThrowsException() {
            activeProvider.setStatus(ProviderStatus.INACTIVE);
            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));

            assertThatThrownBy(() -> aiPromptExecutionService.executePrompt("MATH_HINT", "Prompt", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Tính năng AI hiện đang được bảo trì");
        }

        @Test
        @DisplayName("Khi gọi AI thất bại, hoàn tiền (refund) credit đã đặt chỗ cho người dùng")
        void executePrompt_AiExecutionFails_RefundsCredits() throws Exception {
            Long userId = 42L;
            User studentUser = User.builder().role(Role.STUDENT).build();
            studentUser.setId(userId);

            AiCreditConfig creditConfig = AiCreditConfig.builder()
                    .task("MATH_HINT")
                    .enabled(true)
                    .costPerCall(5)
                    .build();

            when(taskConfigRepository.findByTask("MATH_HINT")).thenReturn(Optional.of(activeTaskConfig));
            when(userRepository.findById(userId)).thenReturn(Optional.of(studentUser));
            when(aiCreditService.getCreditConfig("MATH_HINT")).thenReturn(Optional.of(creditConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);

            when(mockStrategy.executePrompt(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Rate limit reached"));

            assertThatThrownBy(() -> aiPromptExecutionService.executePromptWithResult("MATH_HINT", "Prompt", userId, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Rate limit reached");

            verify(aiCreditService).reserve(eq(userId), eq("MATH_HINT"), anyInt());
            verify(aiCreditService).refund(eq(userId), eq("MATH_HINT"), anyInt());
            verify(aiCreditService, never()).settle(anyLong(), anyString(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("executePromptWithImage & executePromptWithImageWithResult")
    class ExecutePromptWithImageTests {

        @Test
        @DisplayName("Thực thi prompt kèm ảnh Vision thành công")
        void executePromptWithImage_Success() throws Exception {
            when(taskConfigRepository.findByTask("OCR_HANDWRITING")).thenReturn(Optional.of(activeTaskConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);

            AiExecutionResult expected = new AiExecutionResult("Chữ viết tay nhận diện: $x^2 + 2x + 1 = 0$", 80);
            when(mockStrategy.executePromptWithImage(eq(activeProvider), eq(activeTaskConfig), eq("sk-test-secret-key"),
                    eq("Nhận diện ảnh"), eq("base64data"), eq("image/png")))
                    .thenReturn(expected);

            String result = aiPromptExecutionService.executePromptWithImage("OCR_HANDWRITING", "Nhận diện ảnh", "base64data", "image/png", null);

            assertThat(result).isEqualTo("Chữ viết tay nhận diện: $x^2 + 2x + 1 = 0$");
        }

        @Test
        @DisplayName("Khi gọi AI Vision gặp lỗi, hoàn lại tiền đã đặt chỗ")
        void executePromptWithImage_AiFails_RefundsCredits() throws Exception {
            Long userId = 99L;
            User studentUser = User.builder().role(Role.STUDENT).build();
            studentUser.setId(userId);

            AiCreditConfig creditConfig = AiCreditConfig.builder()
                    .task("OCR_HANDWRITING")
                    .enabled(true)
                    .costPerCall(3)
                    .build();

            when(taskConfigRepository.findByTask("OCR_HANDWRITING")).thenReturn(Optional.of(activeTaskConfig));
            when(userRepository.findById(userId)).thenReturn(Optional.of(studentUser));
            when(aiCreditService.getCreditConfig("OCR_HANDWRITING")).thenReturn(Optional.of(creditConfig));
            when(keySelectionService.selectKeyForProvider(activeProvider)).thenReturn(activeApiKey);
            when(aiProviderStrategyFactory.getStrategy(ProviderProtocol.OPENAI_COMPATIBLE)).thenReturn(mockStrategy);

            when(mockStrategy.executePromptWithImage(any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Vision API connection timeout"));

            assertThatThrownBy(() -> aiPromptExecutionService.executePromptWithImageWithResult(
                    "OCR_HANDWRITING", "Nhận diện", "base64", "image/jpeg", userId, true))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Vision API connection timeout");

            verify(aiCreditService).reserve(eq(userId), eq("OCR_HANDWRITING"), anyInt());
            verify(aiCreditService).refund(eq(userId), eq("OCR_HANDWRITING"), anyInt());
        }
    }
}
