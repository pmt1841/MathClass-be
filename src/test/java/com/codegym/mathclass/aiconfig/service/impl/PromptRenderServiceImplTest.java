package com.codegym.mathclass.aiconfig.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.exception.PromptNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptRenderServiceImplTest {

    @Mock
    private SystemPromptRepository systemPromptRepository;

    @InjectMocks
    private PromptRenderServiceImpl promptRenderService;

    private SystemPrompt samplePrompt;

    @BeforeEach
    void setUp() {
        samplePrompt = SystemPrompt.builder()
                .code("MATH_ASSISTANT_PROMPT")
                .name("Prompt Hỗ trợ Toán")
                .taskCode("MATH_HINT")
                .currentContent("Bạn là trợ lý toán cho khối lớp {{grade}}. Hãy giải thích bài toán sau: {{question}}.")
                .build();
    }

    @Test
    @DisplayName("Thay thế chính xác các biến placeholder {{var}} trong template prompt")
    void renderPrompt_Success_ReplacesVariables() {
        when(systemPromptRepository.findByCode("MATH_ASSISTANT_PROMPT")).thenReturn(Optional.of(samplePrompt));

        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("MATH_ASSISTANT_PROMPT")
                .variables(Map.of(
                        "grade", "12",
                        "question", "Tìm nguyên hàm của f(x) = 2x"
                ))
                .build();

        RenderPromptResponse response = promptRenderService.renderPrompt(request);

        assertThat(response).isNotNull();
        assertThat(response.getPromptCode()).isEqualTo("MATH_ASSISTANT_PROMPT");
        assertThat(response.getRenderedPrompt()).isEqualTo("Bạn là trợ lý toán cho khối lớp 12. Hãy giải thích bài toán sau: Tìm nguyên hàm của f(x) = 2x.");
        assertThat(response.getUsedVariables()).containsExactly("grade", "question");
    }

    @Test
    @DisplayName("Xử lý an toàn khi giá trị biến chứa ký tự đặc biệt và công thức LaTeX ($ và \\)")
    void renderPrompt_WithLatexAndSpecialCharacters_EscapesCorrectly() {
        samplePrompt.setCurrentContent("Đề bài: {{formula}}. Lời giải: {{solution}}.");
        when(systemPromptRepository.findByCode("MATH_ASSISTANT_PROMPT")).thenReturn(Optional.of(samplePrompt));

        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("MATH_ASSISTANT_PROMPT")
                .variables(Map.of(
                        "formula", "$f(x) = \\sqrt{x^2 + 1}$",
                        "solution", "Giá trị $100 và ký tự \\ đặc biệt"
                ))
                .build();

        RenderPromptResponse response = promptRenderService.renderPrompt(request);

        assertThat(response.getRenderedPrompt())
                .isEqualTo("Đề bài: $f(x) = \\sqrt{x^2 + 1}$. Lời giải: Giá trị $100 và ký tự \\ đặc biệt.");
    }

    @Test
    @DisplayName("Khi biến truyền vào bị thiếu, thay thế bằng chuỗi rỗng và không gây lỗi")
    void renderPrompt_MissingVariable_ReplacesWithEmptyString() {
        when(systemPromptRepository.findByCode("MATH_ASSISTANT_PROMPT")).thenReturn(Optional.of(samplePrompt));

        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("MATH_ASSISTANT_PROMPT")
                .variables(Map.of("grade", "10")) // thiếu "question"
                .build();

        RenderPromptResponse response = promptRenderService.renderPrompt(request);

        assertThat(response.getRenderedPrompt())
                .isEqualTo("Bạn là trợ lý toán cho khối lớp 10. Hãy giải thích bài toán sau: .");
        assertThat(response.getUsedVariables()).containsExactly("grade", "question");
    }

    @Test
    @DisplayName("Khi request không truyền map biến (variables = null), render bình thường với chuỗi rỗng")
    void renderPrompt_NullVariablesMap_ReplacesWithEmptyStrings() {
        when(systemPromptRepository.findByCode("MATH_ASSISTANT_PROMPT")).thenReturn(Optional.of(samplePrompt));

        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("MATH_ASSISTANT_PROMPT")
                .variables(null)
                .build();

        RenderPromptResponse response = promptRenderService.renderPrompt(request);

        assertThat(response.getRenderedPrompt())
                .isEqualTo("Bạn là trợ lý toán cho khối lớp . Hãy giải thích bài toán sau: .");
    }

    @Test
    @DisplayName("Ném PromptNotFoundException khi mã prompt không tồn tại")
    void renderPrompt_NotFound_ThrowsPromptNotFoundException() {
        when(systemPromptRepository.findByCode("NON_EXISTENT")).thenReturn(Optional.empty());

        RenderPromptRequest request = RenderPromptRequest.builder()
                .promptCode("NON_EXISTENT")
                .build();

        assertThatThrownBy(() -> promptRenderService.renderPrompt(request))
                .isInstanceOf(PromptNotFoundException.class)
                .hasMessageContaining("Không tìm thấy System Prompt với mã code: NON_EXISTENT");
    }
}
