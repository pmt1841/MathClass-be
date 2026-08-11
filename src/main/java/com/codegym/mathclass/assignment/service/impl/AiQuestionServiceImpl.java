package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.GenerateQuestionRequest;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.entity.ApiKey;
import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiconfig.service.KeySelectionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategy;
import com.codegym.mathclass.aiconfig.strategy.AiProviderStrategyFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionServiceImpl implements AiQuestionService {

    private static final String TASK_QUESTION_GEN = "QUESTION_GEN";

    private final KeySelectionService keySelectionService;
    private final TaskConfigRepository taskConfigRepository;
    private final AiProviderStrategyFactory aiProviderStrategyFactory;
    private final PromptRenderService promptRenderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiGeneratedQuestionResponse generateQuestion(GenerateQuestionRequest request) {
        if (request == null || request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Yêu cầu câu hỏi (Prompt) không được để trống");
        }

        TaskConfig taskConfig = taskConfigRepository.findByTask(TASK_QUESTION_GEN)
                .filter(TaskConfig::getEnabled)
                .orElseThrow(() -> new AiGenerationException(503, "Tính năng sinh đề chưa được cấu hình hoặc đã bị tắt trong AI Config."));

        Provider provider = taskConfig.getProvider();
        if (provider == null || provider.getStatus() != ProviderStatus.ACTIVE) {
            throw new AiGenerationException(503, "Provider cấu hình cho việc sinh đề không tồn tại hoặc đã bị tắt.");
        }

        String rawModel = taskConfig.getModel();
        String modelToUse = rawModel != null && rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        String systemPrompt = buildSystemPrompt(request);
        String fullPrompt = systemPrompt + "\n\nYêu cầu từ người dùng:\n" + request.getPrompt();

        Exception lastException = null;
        int maxKeyAttempts = 5;
        int keyAttempts = 0;

        while (keyAttempts < maxKeyAttempts) {
            keyAttempts++;
            ApiKey selectedKey = null;
            String apiKeyString = null;

            try {
                selectedKey = keySelectionService.selectKeyForProvider(provider);
                if (selectedKey != null) {
                    apiKeyString = selectedKey.getEncryptedKey();
                }
            } catch (Exception e) {
                log.warn("Không còn API Key khả dụng từ KeySelectionService: {}", e.getMessage());
            }

            if (apiKeyString == null || apiKeyString.isBlank()) {
                apiKeyString = System.getenv("GEMINI_API_KEY");
            }

            if (apiKeyString == null || apiKeyString.isBlank()) {
                break;
            }

            boolean hasQuotaError = false;

            try {
                log.info("Đang sinh đề bằng model '{}' (Protocol: {}) với Key ID: {}", 
                        modelToUse, provider.getProtocol(), (selectedKey != null ? selectedKey.getId() : "ENV"));

                AiProviderStrategy strategy = aiProviderStrategyFactory.getStrategy(provider.getProtocol());
                AiExecutionResult result = strategy.executePrompt(provider, taskConfig, apiKeyString, fullPrompt);

                AiGeneratedQuestionResponse dto = parseQuestionResponse(result.content());

                if (dto.getGrade() == null) dto.setGrade(request.getGrade());
                if (dto.getDifficulty() == null) dto.setDifficulty(request.getDifficulty());
                if (dto.getTopic() == null) dto.setTopic(request.getTopic());
                dto.setModel(modelToUse);

                if (!hasRequestedExplanation(request.getPrompt())) {
                    dto.setExplanation("");
                }

                boolean shouldDraw = Boolean.TRUE.equals(request.getIncludeCanvasDiagram()) && hasRequestedDrawing(request.getPrompt());
                if (!shouldDraw) {
                    dto.setCanvasData(null);
                }

                return dto;
            } catch (Exception e) {
                lastException = e;
                int statusCode = (e instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                log.warn("Model '{}' với Key ID {} gặp lỗi khi sinh đề bài (HTTP {}): {}", 
                        modelToUse, (selectedKey != null ? selectedKey.getId() : "ENV"), statusCode, errorMsg);

                if (statusCode == 401) {
                    if (selectedKey != null) {
                        keySelectionService.markKeyAsInactive(selectedKey.getId());
                    }
                } else if (statusCode == 429) {
                    hasQuotaError = true;
                }
            }

            if (hasQuotaError && selectedKey != null) {
                keySelectionService.cooldownKey(selectedKey.getId(), 300); // Tạm nghỉ key này 5 phút
            } else if (selectedKey != null && lastException != null && !hasQuotaError) {
                int lastStatus = (lastException instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
                if (lastStatus != 401) {
                    keySelectionService.cooldownKey(selectedKey.getId(), 60); // Tạm nghỉ key 60s để tự động xoay key khác ở lượt thử sau
                }
            }

            if (selectedKey == null) {
                break; // Dùng ENV Key nhưng bị lỗi -> không còn key nào khác trong DB để xoay
            }
        }

        String detailedMsg = lastException != null ? lastException.getMessage() : "Không tìm thấy API Key khả dụng.";
        int finalStatusCode = (lastException instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
        if (finalStatusCode == 429 || detailedMsg.contains("429") || detailedMsg.contains("limit: 0")) {
            log.error("API Key hiện tại của bạn đã dùng hết Quota (Lỗi HTTP 429): {}", detailedMsg);
            throw new AiGenerationException(429, "Hệ thống đang bảo trì. Vui lòng thử lại sau!");
        }

        log.error("Không thể sinh đề bài toán bằng AI (Lỗi HTTP {}): {}", finalStatusCode, detailedMsg);
        throw new AiGenerationException(finalStatusCode, "Hệ thống đang bảo trì. Vui lòng thử lại sau!");
    }

    private AiGeneratedQuestionResponse parseQuestionResponse(String rawResponseBody) {
        try {
            if (rawResponseBody == null || rawResponseBody.isBlank()) {
                throw new AiGenerationException("Phản hồi từ AI bị rỗng.");
            }
            String jsonText = rawResponseBody.trim();
            jsonText = jsonText.replaceAll("(?s)^```[a-z]*\\s*|\\s*```$", "").trim();
            return objectMapper.readValue(jsonText, AiGeneratedQuestionResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Không thể parse JSON từ AI response: {}", e.getMessage());
            throw new AiGenerationException("Dữ liệu phản hồi từ AI không đúng định dạng JSON chuẩn", e);
        } catch (Exception e) {
            throw new AiGenerationException("Lỗi xử lý kết quả sinh bài toán từ AI: " + e.getMessage(), e);
        }
    }

    private String buildSystemPrompt(GenerateQuestionRequest request) {
        boolean isDrawingRequested = Boolean.TRUE.equals(request.getIncludeCanvasDiagram()) && hasRequestedDrawing(request.getPrompt());
        String canvasRequirement = isDrawingRequested
                ? "2. CHÚ Ý: Người dùng CÓ YÊU CẦU vẽ hình/đồ thị, bạn hãy sinh ra object 'canvasData' (chứa điểm, đoạn thẳng, đường tròn, đồ thị hàm số) theo chuẩn JSON."
                : "2. CHÚ Ý: Bài toán này KHÔNG yêu cầu vẽ hình hay đồ thị, TUYỆT ĐỐI KHÔNG sinh ra object 'canvasData' (bỏ qua trường 'canvasData').";

        Map<String, Object> variables = new HashMap<>();
        variables.put("grade_level", request.getGrade() != null ? request.getGrade() : 9);
        variables.put("difficulty", formatDifficultyDescription(request.getDifficulty()));
        variables.put("difficulty_code", request.getDifficulty() != null ? request.getDifficulty() : "THONG_HIEU");
        variables.put("topic", request.getTopic() != null ? request.getTopic() : "Toán học");
        variables.put("question_type", request.getQuestionType() != null ? request.getQuestionType() : "Tự luận");
        variables.put("canvas_requirement", canvasRequirement);

        RenderPromptRequest renderRequest = RenderPromptRequest.builder()
                .promptCode("PROMPT_QUESTION_GEN")
                .variables(variables)
                .build();

        return promptRenderService.renderPrompt(renderRequest).getRenderedPrompt();
    }

    private boolean hasRequestedExplanation(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("lời giải") || lower.contains("giải chi tiết") || lower.contains("hướng dẫn giải")
                || lower.contains("trình bày") || lower.contains("đáp án") || lower.contains("kèm lời giải")
                || lower.contains("có lời giải") || lower.contains("bài giải") || lower.contains("hướng dẫn");
    }

    private boolean hasRequestedDrawing(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String lower = prompt.toLowerCase();
        return lower.contains("vẽ") || lower.contains("đồ thị")
                || lower.contains("minh họa") || lower.contains("sơ đồ") || lower.contains("parabol")
                || lower.contains("vẽ hình") || lower.contains("vẽ đồ thị") || lower.contains("kèm hình") || lower.contains("có hình");
    }

    private String formatDifficultyDescription(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "Thông hiểu (Hiểu bản chất, áp dụng quy tắc trực tiếp, biến đổi đơn giản)";
        }
        return switch (difficulty.toUpperCase().trim()) {
            case "NHAN_BIET" -> "Nhận biết (Tái hiện kiến thức, nhận diện khái niệm/công thức cơ bản, tính toán 1 bước)";
            case "THONG_HIEU" -> "Thông hiểu (Hiểu bản chất, giải thích, biến đổi công thức đơn giản hoặc áp dụng quy tắc trực tiếp)";
            case "VAN_DUNG" -> "Vận dụng (Kết hợp nhiều kiến thức, biến đổi qua nhiều bước tính toán, bài toán ứng dụng thực tế hoặc chứng minh cơ bản)";
            case "VAN_DUNG_CAO" -> "Vận dụng cao (Bài toán nâng cao phân loại học sinh giỏi, tư duy logic phức tạp, kết hợp nhiều chuyên đề hoặc biến đổi khéo léo)";
            default -> difficulty;
        };
    }
}


