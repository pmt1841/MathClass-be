package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.ai.strategy.parser.AiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseParserFactory;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
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
import com.codegym.mathclass.aiqueue.model.payload.AiBatchQuestionJobPayload;
import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.dto.request.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.response.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.dto.response.BatchQuestionItemResponse;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.assignment.service.AiBatchQuestionService;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBatchQuestionServiceImpl implements AiBatchQuestionService {

    public static final String TASK_BATCH_QUESTION_GEN = "BATCH_QUESTION_GEN";
    private static final String TASK_QUESTION_GEN_FALLBACK = "QUESTION_GEN";
    private static final String PROMPT_CODE = "PROMPT_BATCH_QUESTION_GEN";

    private final AssignmentService assignmentService;
    private final KeySelectionService keySelectionService;
    private final TaskConfigRepository taskConfigRepository;
    private final AiProviderStrategyFactory aiProviderStrategyFactory;
    private final PromptRenderService promptRenderService;
    private final AiCreditService aiCreditService;
    private final UserRepository userRepository;
    private final AiResponseParserFactory aiResponseParserFactory;

    @Override
    public BatchGenerateQuestionsResponse batchGenerateQuestions(BatchGenerateQuestionsRequest request, Long userId) {
        return batchGenerateQuestions(request, userId, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public BatchGenerateQuestionsResponse batchGenerateQuestions(BatchGenerateQuestionsRequest request, Long userId, boolean chargeCredits) {
        String documentContent = "";
        List<AssignmentImageResponse> extractedImages = new ArrayList<>();

        if (request.getFile() != null && !request.getFile().isEmpty()) {
            try {
                Map<String, Object> extracted = assignmentService.extractTextFromFile(request.getFile());
                documentContent = (String) extracted.getOrDefault("content", "");
                Object imagesObj = extracted.get("images");
                if (imagesObj instanceof List<?>) {
                    extractedImages = (List<AssignmentImageResponse>) imagesObj;
                }
            } catch (Exception e) {
                log.error("Lỗi khi đọc file tài liệu: {}", e.getMessage(), e);
                throw new BadRequestException("Không thể đọc nội dung file tài liệu: " + e.getMessage());
            }
        } else if (request.getTextContent() != null && !request.getTextContent().isBlank()) {
            documentContent = request.getTextContent().trim();
        }

        if (documentContent.isBlank()) {
            throw new BadRequestException("Nội dung tài liệu/đề bài trống. Vui lòng tải lên file hoặc nhập nội dung.");
        }

        TaskConfig taskConfig = taskConfigRepository.findByTask(TASK_BATCH_QUESTION_GEN)
                .filter(TaskConfig::getEnabled)
                .or(() -> taskConfigRepository.findByTask(TASK_QUESTION_GEN_FALLBACK).filter(TaskConfig::getEnabled))
                .orElseThrow(() -> new AiGenerationException(503,
                        "Tính năng AI hiện đang được bảo trì, vui lòng quay lại sau."));

        Provider provider = taskConfig.getProvider();
        if (provider == null || provider.getStatus() != ProviderStatus.ACTIVE) {
            throw new AiGenerationException(503, "Tính năng AI hiện đang được bảo trì, vui lòng quay lại sau.");
        }

        Optional<AiCreditConfig> creditCfg = aiCreditService.getCreditConfig(TASK_BATCH_QUESTION_GEN);
        boolean charge = chargeCredits
                && creditCfg.isPresent()
                && Boolean.TRUE.equals(creditCfg.get().getEnabled())
                && userId != null
                && !isAdmin(userId);

        int costPerCall = 0;
        Integer tokensPerCredit = null;
        int reserved = 0;
        int userPromptTokens = AiCreditService.estimatePromptTokens(documentContent);
        if (charge) {
            costPerCall = creditCfg.get().getCostPerCall() != null ? creditCfg.get().getCostPerCall() : 2;
            tokensPerCredit = creditCfg.get().getTokensPerCredit() != null ? creditCfg.get().getTokensPerCredit() : 1000;
            reserved = AiCreditService.estimateCredits(userPromptTokens, 2000, costPerCall, tokensPerCredit);
            if (reserved > 0) {
                aiCreditService.reserve(userId, TASK_BATCH_QUESTION_GEN, reserved);
            }
        }

        String rawModel = taskConfig.getModel();
        String modelToUse = rawModel != null && rawModel.startsWith("models/") ? rawModel.substring(7) : rawModel;

        String systemPrompt = buildSystemPrompt(request, documentContent);

        Exception lastException = null;
        int maxKeyAttempts = 5;
        int keyAttempts = 0;

        try {
            while (keyAttempts < maxKeyAttempts) {
                keyAttempts++;
                ApiKey selectedKey = null;

                try {
                    selectedKey = keySelectionService.selectKeyForProvider(provider);
                } catch (Exception e) {
                    log.warn("Không còn API Key khả dụng từ KeySelectionService: {}", e.getMessage());
                }

                if (selectedKey == null || selectedKey.getEncryptedKey() == null || selectedKey.getEncryptedKey().isBlank()) {
                    break;
                }

                String apiKeyString = selectedKey.getEncryptedKey();
                boolean hasQuotaError = false;

                try {
                    log.info("Đang tạo hàng loạt bài tập bằng model '{}' (Protocol: {}) với Key ID: {}",
                            modelToUse, provider.getProtocol(), selectedKey.getId());

                    AiProviderStrategy strategy = aiProviderStrategyFactory.getStrategy(provider.getProtocol());
                    AiExecutionResult result = strategy.executePrompt(provider, taskConfig, apiKeyString, systemPrompt);

                    BatchGenerateQuestionsResponse response = parseBatchResponse(result.content());
                    response.setModel(modelToUse);
                    response.setExtractedImages(extractedImages);
                    response.setCompletionTokens(result.completionTokens());

                    if (response.getQuestions() != null) {
                        for (BatchQuestionItemResponse q : response.getQuestions()) {
                            if (q.getContent() != null) {
                                q.setContent(LaTeXSanitizer.normalizeKatexDelimiters(q.getContent()));
                            }
                            if (q.getExplanation() != null && !q.getExplanation().isBlank()) {
                                q.setExplanation(LaTeXSanitizer.normalizeKatexDelimiters(q.getExplanation()));
                            }
                        }
                        response.setTotalQuestions(response.getQuestions().size());
                    } else {
                        response.setQuestions(new ArrayList<>());
                        response.setTotalQuestions(0);
                    }

                    if (reserved > 0) {
                        int actual = AiCreditService.computeCredits(userPromptTokens, result.completionTokens(), costPerCall, tokensPerCredit);
                        aiCreditService.settle(userId, TASK_BATCH_QUESTION_GEN, reserved, actual);
                        reserved = 0;
                    }

                    return response;
                } catch (Exception e) {
                    lastException = e;
                    int statusCode = (e instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                    log.warn("Model '{}' với Key ID {} gặp lỗi khi tạo hàng loạt bài tập (HTTP {}): {}",
                            modelToUse, selectedKey.getId(), statusCode, errorMsg);

                    if (statusCode == 401) {
                        keySelectionService.markKeyAsInactive(selectedKey.getId());
                    } else if (statusCode == 429) {
                        hasQuotaError = true;
                    }
                }

                if (hasQuotaError) {
                    keySelectionService.cooldownKey(selectedKey.getId(), 300);
                } else if (lastException != null) {
                    int lastStatus = (lastException instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
                    if (lastStatus != 401) {
                        keySelectionService.cooldownKey(selectedKey.getId(), 60);
                    }
                }
            }

            String detailedMsg = lastException != null ? lastException.getMessage() : "Không tìm thấy API Key khả dụng.";
            int finalStatusCode = (lastException instanceof AiGenerationException aiEx) ? aiEx.getStatusCode() : 500;
            if (finalStatusCode == 429 || detailedMsg.contains("429") || detailedMsg.contains("limit: 0")) {
                log.error("API Key hiện tại đã dùng hết Quota (Lỗi HTTP 429): {}", detailedMsg);
                throw new AiGenerationException(429, "Hệ thống đang bảo trì. Vui lòng thử lại sau!");
            }

            log.error("Không thể tạo hàng loạt bài tập bằng AI (Lỗi HTTP {}): {}", finalStatusCode, detailedMsg);
            throw new AiGenerationException(finalStatusCode, "Hệ thống đang bảo trì. Vui lòng thử lại sau!");
        } catch (Exception e) {
            if (reserved > 0) {
                aiCreditService.refund(userId, TASK_BATCH_QUESTION_GEN, reserved);
                reserved = 0;
            }
            throw e;
        } finally {
            if (reserved > 0) {
                aiCreditService.refund(userId, TASK_BATCH_QUESTION_GEN, reserved);
            }
        }
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) return false;
        return userRepository.findById(userId)
                .map(user -> user.getRole() == Role.ADMIN)
                .orElse(false);
    }

    private BatchGenerateQuestionsResponse parseBatchResponse(String rawResponseBody) {
        try {
            if (rawResponseBody == null || rawResponseBody.isBlank()) {
                throw new AiGenerationException("Phản hồi từ AI bị rỗng.");
            }
            AiResponseParser<BatchGenerateQuestionsResponse> parser = aiResponseParserFactory.getParser(AiResponseType.QUESTION);
            return parser.parse(rawResponseBody);
        } catch (Exception e) {
            log.error("Không thể parse JSON từ AI response qua Strategy Engine: {}", e.getMessage());
            throw new AiGenerationException("Dữ liệu phản hồi từ AI không đúng định dạng JSON chuẩn", e);
        }
    }

    private String buildSystemPrompt(BatchGenerateQuestionsRequest request, String documentContent) {
        boolean isExplanationRequested = Boolean.TRUE.equals(request.getIncludeExplanation());
        String explanationRequirement = isExplanationRequested
                ? "Người dùng ĐÃ BẬT tùy chọn kèm lời giải chi tiết. Bạn BẮT BUỘC phải sinh ra trường 'explanation' chứa các bước giải chi tiết, rõ ràng cho từng bài."
                : "Người dùng KHÔNG yêu cầu lời giải. Để trường 'explanation' là chuỗi rỗng \"\" cho mỗi bài tập.";

        String canvasRequirement = Boolean.TRUE.equals(request.getIncludeCanvasDiagram())
                ? "Tạo hình vẽ Canvas nếu bài toán có hình học phẳng hoặc đồ thị."
                : "Không yêu cầu hình vẽ Canvas.";

        Map<String, Object> variables = new HashMap<>();
        variables.put("grade_level", request.getGrade() != null ? request.getGrade() : 9);
        variables.put("topic", request.getTopic() != null && !request.getTopic().isBlank() ? request.getTopic() : "Toán học");
        variables.put("canvas_requirement", canvasRequirement);
        variables.put("explanation_requirement", explanationRequirement);
        variables.put("document_content", documentContent);

        try {
            RenderPromptRequest renderRequest = RenderPromptRequest.builder()
                    .promptCode(PROMPT_CODE)
                    .variables(variables)
                    .build();

            return promptRenderService.renderPrompt(renderRequest).getRenderedPrompt();
        } catch (Exception e) {
            log.warn("Không thể render System Prompt '{}': {}. Dùng fallback prompt.", PROMPT_CODE, e.getMessage());
            return "Bạn là trợ lý Sư phạm Toán học. Hãy bóc tách nội dung tài liệu sau thành danh sách câu hỏi dạng JSON chuẩn: \n\n"
                    + documentContent;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public AiBatchQuestionJobPayload prepareBatchJobPayload(BatchGenerateQuestionsRequest request, Long userId) {
        String textContent = "";
        List<AssignmentImageResponse> extractedImages = new ArrayList<>();

        if (request.getFile() != null && !request.getFile().isEmpty()) {
            try {
                Map<String, Object> extracted = assignmentService.extractTextFromFile(request.getFile());
                textContent = (String) extracted.getOrDefault("content", "");
                Object imagesObj = extracted.get("images");
                if (imagesObj instanceof List<?>) {
                    extractedImages = (List<AssignmentImageResponse>) imagesObj;
                }
            } catch (Exception e) {
                throw new BadRequestException("Không thể đọc nội dung file tài liệu: " + e.getMessage());
            }
        } else if (request.getTextContent() != null && !request.getTextContent().isBlank()) {
            textContent = request.getTextContent().trim();
        }

        if (textContent.isBlank()) {
            throw new BadRequestException("Nội dung tài liệu/đề bài trống. Vui lòng tải lên file hoặc nhập nội dung.");
        }

        return AiBatchQuestionJobPayload.builder()
                .textContent(textContent)
                .extractedImages(extractedImages)
                .grade(request.getGrade())
                .topic(request.getTopic())
                .questionType(request.getQuestionType())
                .includeExplanation(request.getIncludeExplanation())
                .includeCanvasDiagram(request.getIncludeCanvasDiagram())
                .userId(userId)
                .build();
    }
}

