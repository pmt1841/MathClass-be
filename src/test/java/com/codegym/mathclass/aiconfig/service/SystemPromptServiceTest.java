package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.SystemPromptResetRequest;
import com.codegym.mathclass.aiconfig.dto.request.SystemPromptUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.SystemPromptResponse;
import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.entity.SystemPromptHistory;
import com.codegym.mathclass.aiconfig.entity.SystemPromptStatus;
import com.codegym.mathclass.aiconfig.repository.SystemPromptHistoryRepository;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.service.impl.SystemPromptServiceImpl;
import com.codegym.mathclass.aiconfig.validator.SystemPromptValidator;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.InvalidVariableException;
import com.codegym.mathclass.exception.PromptNotFoundException;
import com.codegym.mathclass.aiconfig.dto.request.PromptTestExecuteRequest;
import com.codegym.mathclass.aiconfig.dto.response.PromptTestExecuteResponse;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemPromptServiceTest {

    @Mock
    private SystemPromptRepository systemPromptRepository;

    @Mock
    private SystemPromptHistoryRepository systemPromptHistoryRepository;

    @Spy
    private SystemPromptValidator systemPromptValidator = new SystemPromptValidator();

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SystemPromptServiceImpl systemPromptService;

    private SystemPrompt samplePrompt;

    @BeforeEach
    void setUp() {
        samplePrompt = SystemPrompt.builder()
                .code("PROMPT_SOLVE_HINT")
                .name("Prompt Gợi ý giải toán")
                .taskCode("HINT_EXPLANATION")
                .defaultContent("Bạn là trợ lý giảng dạy môn {{subject}} cấp {{grade_level}}.")
                .currentContent("Bạn là trợ lý giảng dạy môn {{subject}} cấp {{grade_level}}.")
                .allowedVariables("subject,grade_level")
                .description("Mô tả prompt")
                .status(SystemPromptStatus.ACTIVE)
                .build();
        samplePrompt.setId(1L);
    }

    @Test
    @DisplayName("TC-PROMPT-01: Cập nhật System Prompt thành công và tăng version history")
    void testUpdatePrompt_Success() {
        SystemPromptUpdateRequest request = SystemPromptUpdateRequest.builder()
                .name("Prompt Gợi ý giải toán v2")
                .currentContent("Nội dung mới môn {{subject}} cho {{grade_level}}")
                .changeReason("Tối ưu hóa")
                .build();

        when(systemPromptRepository.findById(1L)).thenReturn(Optional.of(samplePrompt));
        when(systemPromptRepository.save(any(SystemPrompt.class))).thenReturn(samplePrompt);

        SystemPromptResponse response = systemPromptService.updatePrompt(1L, request, "admin@mathclass.edu.vn", "127.0.0.1");

        assertNotNull(response);
        verify(systemPromptHistoryRepository).save(any(SystemPromptHistory.class));
        verify(systemLogService).log(eq("admin@mathclass.edu.vn"), eq("UPDATE_PROMPT"), any(), any(), eq("PROMPT_SOLVE_HINT"), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PROMPT-04: Khôi phục mặc định Reset to Default thành công")
    void testResetToDefault_Success() {
        samplePrompt.setCurrentContent("Nội dung đã bị sửa sai...");

        when(systemPromptRepository.findById(1L)).thenReturn(Optional.of(samplePrompt));
        when(systemPromptRepository.save(any(SystemPrompt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemPromptResetRequest request = SystemPromptResetRequest.builder().reason("Reset về bản gốc").build();
        SystemPromptResponse response = systemPromptService.resetToDefault(1L, request, "admin@mathclass.edu.vn", "127.0.0.1");

        assertEquals(samplePrompt.getDefaultContent(), response.getCurrentContent());
        verify(systemPromptHistoryRepository).save(any(SystemPromptHistory.class));
        verify(systemLogService).log(eq("admin@mathclass.edu.vn"), eq("RESET_PROMPT"), any(), any(), eq("PROMPT_SOLVE_HINT"), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PROMPT-05: Rollback về phiên bản lịch sử chỉ định")
    void testRollbackToVersion_Success() {
        SystemPromptHistory historyVersion1 = SystemPromptHistory.builder()
                .prompt(samplePrompt)
                .version(1)
                .content("Nội dung phiên bản 1 chuẩn {{subject}}")
                .createdBy("admin@mathclass.edu.vn")
                .build();
        historyVersion1.setId(10L);

        when(systemPromptRepository.findById(1L)).thenReturn(Optional.of(samplePrompt));
        when(systemPromptHistoryRepository.findById(10L)).thenReturn(Optional.of(historyVersion1));
        when(systemPromptRepository.save(any(SystemPrompt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemPromptResponse response = systemPromptService.rollbackToVersion(1L, 10L, "admin@mathclass.edu.vn", "127.0.0.1");

        assertEquals("Nội dung phiên bản 1 chuẩn {{subject}}", response.getCurrentContent());
        verify(systemLogService).log(eq("admin@mathclass.edu.vn"), eq("ROLLBACK_PROMPT"), any(), any(), eq("PROMPT_SOLVE_HINT"), any(), any(), any());
    }

    @Test
    @DisplayName("TC-PROMPT-06: Ném lỗi khi không tìm thấy Prompt với ID")
    void testGetPromptById_NotFound_ThrowsException() {
        when(systemPromptRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PromptNotFoundException.class, () -> systemPromptService.getPromptById(99L));
    }

    @Test
    @DisplayName("TC-PROMPT-07: Chạy thử nghiệm Prompt thành công với promptCode có sẵn")
    void testTestExecutePrompt_Success() {
        when(systemPromptRepository.findByCode("PROMPT_SOLVE_HINT")).thenReturn(Optional.of(samplePrompt));
        when(aiPromptExecutionService.executePrompt(eq("HINT_EXPLANATION"), anyString(), any()))
                .thenReturn("Chào em, để giải phương trình x^2 - 4 = 0, em có thể dùng hằng đẳng thức...");

        PromptTestExecuteRequest request = PromptTestExecuteRequest.builder()
                .promptCode("PROMPT_SOLVE_HINT")
                .variables(Map.of("subject", "Toán học", "grade_level", "Lớp 10"))
                .build();

        PromptTestExecuteResponse response = systemPromptService.testExecutePrompt(request, "admin@mathclass.edu.vn");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("PROMPT_SOLVE_HINT", response.getPromptCode());
        assertEquals("HINT_EXPLANATION", response.getTaskCode());
        assertTrue(response.getRenderedPrompt().contains("Toán học"));
        assertTrue(response.getRenderedPrompt().contains("Lớp 10"));
        assertNotNull(response.getAiResponse());
    }

    @Test
    @DisplayName("TC-PROMPT-08: Chạy thử nghiệm Prompt với customContent nháp")
    void testTestExecutePrompt_WithCustomContent() {
        when(aiPromptExecutionService.executePrompt(eq("SUBMISSION_GRADING"), anyString(), any()))
                .thenReturn("{\"score\": 9, \"feedback\": \"Tốt\"}");

        PromptTestExecuteRequest request = PromptTestExecuteRequest.builder()
                .taskCode("SUBMISSION_GRADING")
                .customContent("Chấm điểm bài làm: {{student_answer}}")
                .variables(Map.of("student_answer", "x = 2"))
                .build();

        PromptTestExecuteResponse response = systemPromptService.testExecutePrompt(request, "admin@mathclass.edu.vn");

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("SUBMISSION_GRADING", response.getTaskCode());
        assertEquals("Chấm điểm bài làm: x = 2", response.getRenderedPrompt());
        assertEquals("{\"score\": 9, \"feedback\": \"Tốt\"}", response.getAiResponse());
    }

    @Test
    @DisplayName("TC-PROMPT-09: Xử lý lỗi khi AI Provider gặp sự cố kết nối trong testExecutePrompt")
    void testTestExecutePrompt_AiErrorHandled() {
        when(systemPromptRepository.findByCode("PROMPT_SOLVE_HINT")).thenReturn(Optional.of(samplePrompt));
        when(aiPromptExecutionService.executePrompt(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("API Key hết quota"));

        PromptTestExecuteRequest request = PromptTestExecuteRequest.builder()
                .promptCode("PROMPT_SOLVE_HINT")
                .build();

        PromptTestExecuteResponse response = systemPromptService.testExecutePrompt(request, "admin@mathclass.edu.vn");

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertNull(response.getAiResponse());
        assertTrue(response.getErrorMessage().contains("API Key hết quota"));
    }
}

