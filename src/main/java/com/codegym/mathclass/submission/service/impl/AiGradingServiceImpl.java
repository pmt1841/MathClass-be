package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.dto.response.DrawingIssueItem;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.AiGradingService;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MAT-250: Triển khai AI chấm sơ bộ.
 *
 * Luồng xử lý:
 * 1. Kiểm tra bài nộp tồn tại, giáo viên sở hữu bài tập, học sinh đã NỘP bài (không phải DRAFT).
 * 2. Build prompt gồm đề bài (kèm hình vẽ Canvas mẫu) + bài làm học sinh (kèm hình vẽ học sinh).
 * 3. Gọi AI theo task config {@code SUBMISSION_GRADING} (admin cấu hình tại trang AI Config).
 * 4. Parse phản hồi JSON, clamp điểm theo maxScore, xác định hasCanvasComparison server-side.
 *
 * Kết quả chỉ là DỰ THẢO — không ghi vào score/teacherFeedback của Submission.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradingServiceImpl implements AiGradingService {

    /** Task code tương ứng với "Chấm bài Tự luận AI" trong trang Admin AI Config. */
    private static final String GRADING_TASK_CODE = "SUBMISSION_GRADING";

    /** Giới hạn độ dài văn bản (trừ block hình vẽ) gửi vào prompt để tiết kiệm token. */
    private static final int MAX_TEXT_LENGTH = 4000;

    /** Giới hạn độ dài block hình vẽ Canvas gửi vào prompt (tránh vượt context của model). */
    private static final int MAX_DRAWINGS_LENGTH = 8000;

    /** Số lần thử tối đa khi AI trả về phản hồi rỗng (tránh lỗi tạm thời của LLM). */
    private static final int MAX_EMPTY_RESPONSE_ATTEMPTS = 2;

    private static final Pattern DRAWINGS_BLOCK_PATTERN =
            Pattern.compile("(?s)<!-- DRAWINGS_DATA_START\\n.*?\\nDRAWINGS_DATA_END -->");

    private final SubmissionRepository submissionRepository;
    private final AiPromptExecutionService aiPromptExecutionService;
    private final PromptRenderService promptRenderService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);

    @Override
    public AiGradingResponse requestAiGrading(long submissionId, AiGradingRequest request, long teacherId) {
        return requestAiGrading(submissionId, request, teacherId, true);
    }

    @Override
    public AiGradingResponse requestAiGrading(long submissionId, AiGradingRequest request, long teacherId, boolean chargeCredits) {
        Submission submission = submissionRepository.findByIdWithDetails(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        Assignment assignment = submission.getAssignment();
        // So sánh Long an toàn (tránh lỗi auto-unboxing với ID > 127, theo UT-BE-10)
        if (assignment == null || !Objects.equals(assignment.getTeacher().getId(), teacherId)) {
            throw new AccessDeniedException("Bạn không có quyền chấm bài nộp này");
        }

        if (submission.getStatus() == SubmissionStatus.DRAFT) {
            throw new BadRequestException("Học sinh chưa nộp bài");
        }

        String prompt = buildGradingPrompt(assignment, submission);
        AiExecutionResult execResult = executePromptWithRetryOnEmpty(prompt, teacherId, chargeCredits);

        AiGradingResponse response = parseAiResponse(execResult.content(), assignment, submission);
        response.setCompletionTokens(execResult.completionTokens());
        return response;
    }

    /**
     * Gọi AI chấm bài, tự thử lại tối đa {@value #MAX_EMPTY_RESPONSE_ATTEMPTS} lần
     * khi model trả về phản hồi rỗng (hiện tượng tạm thời phổ biến của LLM).
     * Vẫn rỗng sau khi thử lại → ném lỗi rõ ràng kèm task code để admin kiểm tra config.
     * Lỗi runtime từ dịch vụ AI (timeout, kết nối...) được bọc thành BadRequestException
     * kèm nguyên nhân thật để frontend hiển thị được (thay vì 500 mặc định).
     */
    private AiExecutionResult executePromptWithRetryOnEmpty(String prompt, long teacherId, boolean chargeCredits) {
        AiExecutionResult result = null;
        for (int attempt = 1; attempt <= MAX_EMPTY_RESPONSE_ATTEMPTS; attempt++) {
            try {
                result = aiPromptExecutionService.executePromptWithResult(GRADING_TASK_CODE, prompt, teacherId, chargeCredits);
            } catch (RuntimeException e) {
                String cause = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định từ dịch vụ AI";
                log.error("Gọi AI chấm bài thất bại (lần thử {}/{}): {}", attempt, MAX_EMPTY_RESPONSE_ATTEMPTS, cause, e);
                throw new BadRequestException("AI chấm bài tạm thời không khả dụng: " + cause);
            }
            if (result != null && result.content() != null && !result.content().isBlank()) {
                return result;
            }
            log.warn("AI chấm bài trả về phản hồi rỗng (lần thử {}/{}) cho task '{}'",
                    attempt, MAX_EMPTY_RESPONSE_ATTEMPTS, GRADING_TASK_CODE);
        }
        throw new BadRequestException("AI phản hồi rỗng (task " + GRADING_TASK_CODE
                + "). Vui lòng kiểm tra cấu hình Provider/Model trên trang Admin AI Config hoặc thử lại sau.");
    }

    private String buildGradingPrompt(Assignment assignment, Submission submission) {
        double maxScore = assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0;
        String title = assignment.getTitle() != null ? assignment.getTitle() : "Bài tập Toán";
        String problemContent = buildContentWithDrawings(assignment.getContent());
        String studentContent = buildContentWithDrawings(submission.getContent());

        Map<String, Object> variables = Map.of(
                "title", title,
                "max_score", maxScore,
                "problem_content", problemContent,
                "student_content", studentContent,
                "subject", "Toán học"
        );

        RenderPromptRequest renderRequest = RenderPromptRequest.builder()
                .promptCode("PROMPT_SUBMISSION_GRADING")
                .variables(variables)
                .build();

        RenderPromptResponse renderResponse = promptRenderService.renderPrompt(renderRequest);

        if (renderResponse == null || renderResponse.getRenderedPrompt() == null || renderResponse.getRenderedPrompt().isBlank()) {
            throw new ResourceNotFoundException("Chưa cấu hình System Prompt 'PROMPT_SUBMISSION_GRADING' trong CSDL.");
        }

        return renderResponse.getRenderedPrompt();
    }

    private AiGradingResponse parseAiResponse(String raw, Assignment assignment, Submission submission) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("AI phản hồi rỗng. Vui lòng thử lại.");
        }

        double maxScore = assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0;
        boolean hasCanvasComparison = extractDrawingsBlock(assignment.getContent()) != null;

        try {
            String json = AiResponseUtils.extractCleanJson(raw);
            JsonNode root = objectMapper.readTree(json);

            String draftFeedback = root.hasNonNull("draftFeedback") ? root.get("draftFeedback").asText() : "";
            draftFeedback = normalizeKatexDelimiters(draftFeedback);

            AiGradingResponse response = AiGradingResponse.builder()
                    .suggestedScore(root.hasNonNull("suggestedScore") ? root.get("suggestedScore").asDouble() : null)
                    .draftFeedback(draftFeedback)
                    .hasCanvasComparison(hasCanvasComparison)
                    .drawingIssues(parseDrawingIssues(root, hasCanvasComparison))
                    .build();

            if (response.getSuggestedScore() != null) {
                double score = Math.max(0, Math.min(response.getSuggestedScore(), maxScore));
                response.setSuggestedScore(Math.round(score * 10.0) / 10.0);
            }
            return response;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không parse được bằng Jackson, thử parse fallback bằng regex cho submissionId={}: {}", submission.getId(), raw);
            AiGradingResponse fallback = tryParseFallback(raw, maxScore, hasCanvasComparison);
            if (fallback != null) {
                return fallback;
            }
            log.error("Không parse được phản hồi AI chấm bài (submissionId={}): {}", submission.getId(), raw);
            throw new BadRequestException("AI phản hồi không đúng định dạng. Vui lòng thử lại.");
        }
    }

    private AiGradingResponse tryParseFallback(String raw, double maxScore, boolean hasCanvasComparison) {
        if (raw == null || raw.isBlank()) return null;
        try {
            Double suggestedScore = null;
            Matcher scoreMatcher = Pattern.compile("\"suggestedScore\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(raw);
            if (scoreMatcher.find()) {
                suggestedScore = Double.parseDouble(scoreMatcher.group(1));
                suggestedScore = Math.max(0, Math.min(suggestedScore, maxScore));
                suggestedScore = Math.round(suggestedScore * 10.0) / 10.0;
            }

            String draftFeedback = "";
            Matcher feedbackMatcher = Pattern.compile("\"draftFeedback\"\\s*:\\s*\"([\\s\\S]*?)(?:\"\\s*,|\"\\s*\\}|$)").matcher(raw);
            if (feedbackMatcher.find()) {
                draftFeedback = feedbackMatcher.group(1).trim();
                while (draftFeedback.endsWith("\\")) {
                    draftFeedback = draftFeedback.substring(0, draftFeedback.length() - 1).trim();
                }
            } else if (raw.contains("\"draftFeedback\"")) {
                int idx = raw.indexOf("\"draftFeedback\"");
                int colonIdx = raw.indexOf(':', idx);
                if (colonIdx != -1) {
                    String sub = raw.substring(colonIdx + 1).trim();
                    if (sub.startsWith("\"")) {
                        sub = sub.substring(1);
                    }
                    sub = sub.replaceAll("[\"}\\s]+$", "");
                    while (sub.endsWith("\\")) {
                        sub = sub.substring(0, sub.length() - 1).trim();
                    }
                    draftFeedback = sub;
                }
            }

            if (suggestedScore != null || !draftFeedback.isBlank()) {
                return AiGradingResponse.builder()
                        .suggestedScore(suggestedScore)
                        .draftFeedback(normalizeKatexDelimiters(draftFeedback))
                        .hasCanvasComparison(hasCanvasComparison)
                        .drawingIssues(new ArrayList<>())
                        .build();
            }
        } catch (Exception ex) {
            log.warn("Lỗi khi chạy fallback parse AI grading: {}", ex.getMessage());
        }
        return null;
    }

    private List<DrawingIssueItem> parseDrawingIssues(JsonNode root, boolean hasCanvasComparison) {
        List<DrawingIssueItem> issues = new ArrayList<>();
        if (!hasCanvasComparison) {
            return issues;
        }
        JsonNode arrayNode = root.path("drawingIssues");
        if (arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String issue = normalizeKatexDelimiters(node.path("issue").asText(""));
                String detail = normalizeKatexDelimiters(node.path("detail").asText(""));
                if (!issue.isBlank()) {
                    issues.add(DrawingIssueItem.builder().issue(issue).detail(detail).build());
                }
            }
        }
        return issues;
    }

    private String normalizeKatexDelimiters(String content) {
        return LaTeXSanitizer.normalizeKatexDelimiters(content);
    }

    /**
     * Trích xuất block hình vẽ Canvas (<!-- DRAWINGS_DATA_START ... -->) nếu có.
     */
    private String extractDrawingsBlock(String content) {
        if (content == null) return null;
        Matcher matcher = DRAWINGS_BLOCK_PATTERN.matcher(content);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * Giữ lại nội dung văn bản (giới hạn độ dài) + đính kèm block hình vẽ Canvas nếu có.
     * Block hình vẽ là phần quan trọng để AI đối chiếu nên không cắt hoàn toàn,
     * nhưng vẫn giới hạn độ dài để tránh vượt context của model.
     */
    private String buildContentWithDrawings(String content) {
        if (content == null) return "[Không có nội dung]";
        String drawingsBlock = extractDrawingsBlock(content);
        String clean = content.replaceAll("(?s)<!-- DRAWINGS_DATA_START\\n.*?\\nDRAWINGS_DATA_END -->", "").trim();

        StringBuilder sb = new StringBuilder();
        if (clean.isBlank()) {
            sb.append("[Không có nội dung văn bản]");
        } else if (clean.length() > MAX_TEXT_LENGTH) {
            sb.append(clean, 0, MAX_TEXT_LENGTH).append("\n...[nội dung bị cắt do quá dài]...");
        } else {
            sb.append(clean);
        }
        if (drawingsBlock != null) {
            sb.append("\n").append(truncateHead(drawingsBlock, MAX_DRAWINGS_LENGTH));
        }
        return sb.toString();
    }

    /** Cắt bớt phần đuôi nếu chuỗi vượt quá maxLength (giữ phần đầu chứa shapeCode + elements). */
    private String truncateHead(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "\n...[dữ liệu hình vẽ bị cắt do quá dài]...";
    }
}

