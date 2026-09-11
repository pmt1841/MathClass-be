package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.TaskConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.TaskConfigResponse;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.ProviderRepository;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskConfigServiceImplTest {

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private TaskConfigServiceImpl taskConfigService;

    private Provider sampleProvider;
    private TaskConfig sampleTaskConfig;

    @BeforeEach
    void setUp() {
        sampleProvider = Provider.builder()
                .code("OPENAI")
                .name("OpenAI")
                .status(ProviderStatus.ACTIVE)
                .protocol(ProviderProtocol.OPENAI_COMPATIBLE)
                .build();
        sampleProvider.setId(1L);

        sampleTaskConfig = TaskConfig.builder()
                .task("BATCH_QUESTION_GEN")
                .provider(sampleProvider)
                .model("gpt-4o")
                .temperature(new BigDecimal("0.5"))
                .maxToken(4000)
                .enabled(true)
                .build();
        sampleTaskConfig.setId(10L);
    }

    @Test
    @DisplayName("Lấy cấu hình TaskConfig thành công theo tên task")
    void getTaskConfig_Success() {
        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(sampleTaskConfig));

        TaskConfigResponse response = taskConfigService.getTaskConfig("batch_question_gen");

        assertThat(response).isNotNull();
        assertThat(response.getTask()).isEqualTo("BATCH_QUESTION_GEN");
        assertThat(response.getProviderId()).isEqualTo(1L);
        assertThat(response.getModel()).isEqualTo("gpt-4o");
        assertThat(response.getMaxToken()).isEqualTo(4000);
        assertThat(response.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi không tìm thấy cấu hình task")
    void getTaskConfig_NotFound_ThrowsResourceNotFoundException() {
        when(taskConfigRepository.findByTask("UNKNOWN_TASK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskConfigService.getTaskConfig("UNKNOWN_TASK"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Chưa có cấu hình cho Task: UNKNOWN_TASK");
    }

    @Test
    @DisplayName("Cập nhật thành công cấu hình TaskConfig đã tồn tại")
    void updateTaskConfig_ExistingTask_Success() {
        TaskConfigUpdateRequest request = TaskConfigUpdateRequest.builder()
                .providerId(1L)
                .model("gpt-4o-mini")
                .temperature(new BigDecimal("0.8"))
                .maxToken(2048)
                .enabled(false)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));
        when(taskConfigRepository.findByTask("BATCH_QUESTION_GEN")).thenReturn(Optional.of(sampleTaskConfig));
        when(taskConfigRepository.save(any(TaskConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskConfigResponse response = taskConfigService.updateTaskConfig("batch_question_gen", request);

        assertThat(response).isNotNull();
        assertThat(response.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(response.getMaxToken()).isEqualTo(2048);
        assertThat(response.getEnabled()).isFalse();
        verify(taskConfigRepository).save(any(TaskConfig.class));
    }

    @Test
    @DisplayName("Tự động tạo mới TaskConfig khi task chưa từng được cấu hình")
    void updateTaskConfig_NewTask_Success() {
        TaskConfigUpdateRequest request = TaskConfigUpdateRequest.builder()
                .providerId(1L)
                .model("claude-3-5-sonnet")
                .temperature(new BigDecimal("0.3"))
                .maxToken(3000)
                .enabled(true)
                .build();

        when(providerRepository.findById(1L)).thenReturn(Optional.of(sampleProvider));
        when(taskConfigRepository.findByTask("AI_GRADING")).thenReturn(Optional.empty());
        when(taskConfigRepository.save(any(TaskConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskConfigResponse response = taskConfigService.updateTaskConfig("ai_grading", request);

        assertThat(response).isNotNull();
        assertThat(response.getTask()).isEqualTo("AI_GRADING");
        assertThat(response.getModel()).isEqualTo("claude-3-5-sonnet");
        assertThat(response.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Ném ResourceNotFoundException khi providerId trong request không tồn tại")
    void updateTaskConfig_ProviderNotFound_ThrowsResourceNotFoundException() {
        TaskConfigUpdateRequest request = TaskConfigUpdateRequest.builder()
                .providerId(999L)
                .model("model")
                .temperature(new BigDecimal("0.5"))
                .maxToken(1000)
                .enabled(true)
                .build();

        when(providerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskConfigService.updateTaskConfig("any_task", request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy Provider với ID: 999");
    }
}
