package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MAT-250: Triển khai AI chấm sơ bộ.
 *
 * Luồng xử lý:
 * 1. Kiểm tra bài nộp tồn tại, giáo viên sở hữu bài tập, học sinh đã NỘP bài (không phải DRAFT).
 * 2. Build prompt gồm đề bài (kèm hình vẽ Canvas mẫu) + bài làm học sinh (kèm hình vẽ học sinh).
 * 3. Gọi AI theo task config {@code ASSIGNMENT_GRADING} (admin cấu hình tại trang AI Config).
 * 4. Parse phản hồi JSON, clamp điểm theo maxScore, xác định hasCanvasComparison server-side.
 *
 * Kết quả chỉ là DỰ THẢO — không ghi vào score/teacherFeedback của Submission.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiGradingServiceImpl implements AiGradingService {

    /** Task code tương ứng với "Chấm bài Tự luận AI" trong trang Admin AI Config. */
    private static final String GRADING_TASK_CODE = "ASSIGNMENT_GRADING";

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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiGradingResponse requestAiGrading(long submissionId, AiGradingRequest request, long teacherId) {
        Submission submission = submissionRepository.findById(submissionId)
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
        String rawAiResponse = executePromptWithRetryOnEmpty(prompt, teacherId);

        return parseAiResponse(rawAiResponse, assignment, submission);
    }

    /**
     * Gọi AI chấm bài, tự thử lại tối đa {@value #MAX_EMPTY_RESPONSE_ATTEMPTS} lần
     * khi model trả về phản hồi rỗng (hiện tượng tạm thời phổ biến của LLM).
     * Vẫn rỗng sau khi thử lại → ném lỗi rõ ràng kèm task code để admin kiểm tra config.
     * Lỗi runtime từ dịch vụ AI (timeout, kết nối...) được bọc thành BadRequestException
     * kèm nguyên nhân thật để frontend hiển thị được (thay vì 500 mặc định).
     */
    private String executePromptWithRetryOnEmpty(String prompt, long teacherId) {
        String raw = null;
        for (int attempt = 1; attempt <= MAX_EMPTY_RESPONSE_ATTEMPTS; attempt++) {
            try {
                raw = aiPromptExecutionService.executePrompt(GRADING_TASK_CODE, prompt, teacherId);
            } catch (RuntimeException e) {
                String cause = e.getMessage() != null ? e.getMessage() : "Lỗi không xác định từ dịch vụ AI";
                log.error("Gọi AI chấm bài thất bại (lần thử {}/{}): {}", attempt, MAX_EMPTY_RESPONSE_ATTEMPTS, cause, e);
                throw new BadRequestException("AI chấm bài tạm thời không khả dụng: " + cause);
            }
            if (raw != null && !raw.isBlank()) {
                return raw;
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

        return String.format("""
                [ĐỀ BÀI TOÁN]:
                Tiêu đề: %s
                Thang điểm tối đa: %s
                Nội dung đề (nếu có hình vẽ Canvas mẫu thì nằm trong comment <!-- DRAWINGS_DATA_START -->):
                %s

                [BÀI LÀM CỦA HỌC SINH] (hình vẽ Canvas học sinh vẽ nếu có nằm trong comment <!-- DRAWINGS_DATA_START -->):
                %s

                Nhiệm vụ:
                1. So sánh hình vẽ Canvas của học sinh với hình mẫu trong đề bài, liệt kê các lỗi sai cụ thể
                   (ví dụ: vẽ thiếu đường cao, sai góc, sai tiệm cận đồ thị). Nếu bài tập không có hình mẫu hoặc
                   học sinh không vẽ hình thì để drawingIssues = [].
                2. Chấm điểm sơ bộ bài tự luận theo thang %s và viết DỰ THẢO lời nhận xét chi tiết bằng tiếng Việt,
                   chỉ ra từng lỗi sai cụ thể trong lời giải, hỗ trợ Markdown và LaTeX ($...$).

                Phản hồi CHỈ trả về một JSON hợp lệ, KHÔNG kèm văn bản hay giải thích bên ngoài, đúng schema:
                {"suggestedScore": 8.5, "draftFeedback": "...", "drawingIssues": [{"issue": "...", "detail": "..."}]}
                """, title, maxScore, problemContent, studentContent, maxScore);
    }

    private AiGradingResponse parseAiResponse(String raw, Assignment assignment, Submission submission) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("AI phản hồi rỗng. Vui lòng thử lại.");
        }

        double maxScore = assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0;
        boolean hasCanvasComparison = extractDrawingsBlock(assignment.getContent()) != null;

        try {
            String json = extractJson(raw);
            JsonNode root = objectMapper.readTree(json);

            AiGradingResponse response = AiGradingResponse.builder()
                    .suggestedScore(root.hasNonNull("suggestedScore") ? root.get("suggestedScore").asDouble() : null)
                    .draftFeedback(root.hasNonNull("draftFeedback") ? root.get("draftFeedback").asText() : "")
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
            log.error("Không parse được phản hồi AI chấm bài (submissionId={}): {}", submission.getId(), raw);
            throw new BadRequestException("AI phản hồi không đúng định dạng. Vui lòng thử lại.");
        }
    }

    private List<DrawingIssueItem> parseDrawingIssues(JsonNode root, boolean hasCanvasComparison) {
        List<DrawingIssueItem> issues = new ArrayList<>();
        if (!hasCanvasComparison) {
            return issues;
        }
        JsonNode arrayNode = root.path("drawingIssues");
        if (arrayNode.isArray()) {
            for (JsonNode node : arrayNode) {
                String issue = node.path("issue").asText("");
                String detail = node.path("detail").asText("");
                if (!issue.isBlank()) {
                    issues.add(DrawingIssueItem.builder().issue(issue).detail(detail).build());
                }
            }
        }
        return issues;
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

    /**
     * Trích xuất chuỗi JSON từ phản hồi AI: bỏ code fence ```json ... ``` nếu có,
     * lấy từ dấu '{' đầu tiên đến dấu '}' cuối cùng.
     */
    private String extractJson(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }
}

