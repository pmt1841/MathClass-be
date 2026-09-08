package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.ai.strategy.parser.AiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseParserFactory;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
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
import com.codegym.mathclass.utils.LaTeXSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final AiResponseParserFactory aiResponseParserFactory;

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
                if (result == null || result.content() == null) {
                    String text = aiPromptExecutionService.executePrompt(GRADING_TASK_CODE, prompt, teacherId);
                    if (text != null) {
                        result = new AiExecutionResult(text, null);
                    }
                }
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
            AiResponseParser<AiGradingResponse> parser = aiResponseParserFactory.getParser(AiResponseType.GRADING);
            AiGradingResponse response = parser.parse(raw);

            if (response.getDraftFeedback() != null) {
                response.setDraftFeedback(normalizeKatexDelimiters(response.getDraftFeedback()));
            }
            response.setHasCanvasComparison(hasCanvasComparison);

            if (!hasCanvasComparison) {
                response.setDrawingIssues(new ArrayList<>());
            } else if (response.getDrawingIssues() == null) {
                response.setDrawingIssues(new ArrayList<>());
            }

            if (response.getSuggestedScore() != null) {
                double score = Math.max(0, Math.min(response.getSuggestedScore(), maxScore));
                response.setSuggestedScore(Math.round(score * 10.0) / 10.0);
            }
            return response;
        } catch (BadRequestException e) {
            throw e;
        } catch (com.codegym.mathclass.ai.strategy.parser.exception.AiParsingException e) {
            throw new BadRequestException("AI phản hồi không đúng định dạng. Vui lòng thử lại.");
        } catch (Exception e) {
            log.error("Không parse được phản hồi AI chấm bài (submissionId={}): {}", submission.getId(), raw);
            throw new BadRequestException("AI phản hồi không đúng định dạng. Vui lòng thử lại.");
        }
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

