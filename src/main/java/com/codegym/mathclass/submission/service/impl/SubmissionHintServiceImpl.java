package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.HintLimitExceededException;
import com.codegym.mathclass.exception.InvalidSubmissionStateException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.StudentHintRequest;
import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.codegym.mathclass.submission.dto.response.SubmissionHintItemResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionHint;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionHintRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.submission.service.SubmissionHintService;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionHintServiceImpl implements SubmissionHintService {

    private final SubmissionHintRepository submissionHintRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final AiPromptExecutionService aiPromptExecutionService;

    private static final int MAX_HINTS = 3;

    @Override
    @Transactional
    public StudentHintResponse requestHint(Long assignmentId, StudentHintRequest request, String studentEmail) {
        try {
            User student = userRepository.findByEmail(studentEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học sinh với email: " + studentEmail));

            Assignment assignment = assignmentRepository.findById(assignmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài tập với ID: " + assignmentId));

            if (assignment.getDeadline() != null && LocalDateTime.now().isAfter(assignment.getDeadline())) {
                throw new BadRequestException("Đã hết hạn làm bài tập, không thể xin gợi ý.");
            }

            Submission submission = submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, student.getId())
                    .orElseGet(() -> submissionRepository.save(Submission.builder()
                            .assignment(assignment)
                            .student(student)
                            .content("")
                            .status(SubmissionStatus.DRAFT)
                            .build()));

            if (submission.getStatus() == SubmissionStatus.SUBMITTED || submission.getStatus() == SubmissionStatus.GRADED) {
                throw new InvalidSubmissionStateException("Bài nộp đã gửi hoặc đã được chấm điểm, không thể xin gợi ý.");
            }

            int usedCount = submissionHintRepository.countBySubmissionId(submission.getId());
            if (usedCount >= MAX_HINTS) {
                throw new HintLimitExceededException("Bạn đã sử dụng tối đa " + MAX_HINTS + "/" + MAX_HINTS + " lượt gợi ý cho bài tập này.");
            }

            String rawContent = request != null && request.getCurrentContent() != null ? request.getCurrentContent() : "";
            String sanitizedContent = sanitizeContent(rawContent);

            String prompt = buildSocraticPrompt(assignment, sanitizedContent);

            String aiHintContent = aiPromptExecutionService.executePrompt("STUDENT_HINT", prompt, student.getId());

            int hintNumber = usedCount + 1;
            SubmissionHint hintRecord = SubmissionHint.builder()
                    .submission(submission)
                    .student(student)
                    .hintNumber(hintNumber)
                    .studentSnapshotContent(sanitizedContent)
                    .aiHintContent(aiHintContent)
                    .build();

            SubmissionHint saved = submissionHintRepository.save(hintRecord);

            return StudentHintResponse.builder()
                    .id(saved.getId())
                    .submissionId(submission.getId())
                    .hintNumber(hintNumber)
                    .maxHints(MAX_HINTS)
                    .remainingHints(MAX_HINTS - hintNumber)
                    .hintContent(aiHintContent)
                    .createdAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now())
                    .build();
        } catch (BadRequestException | ResourceNotFoundException | AccessDeniedException e) {
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public HintHistoryResponse getHintHistory(Long submissionId, String currentUserEmail) {
        User currentUser = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));

        Submission submission = submissionRepository.findByIdWithDetails(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài nộp"));

        boolean isOwner = Objects.equals(submission.getStudent().getId(), currentUser.getId());
        boolean isTeacherOrAdmin = Objects.equals(submission.getAssignment().getTeacher().getId(), currentUser.getId());

        if (!isOwner && !isTeacherOrAdmin) {
            throw new AccessDeniedException("Bạn không có quyền xem lịch sử gợi ý này");
        }

        List<SubmissionHint> hints = submissionHintRepository.findBySubmissionIdOrderByHintNumberAsc(submissionId);
        int totalUsed = hints.size();

        List<SubmissionHintItemResponse> items = hints.stream()
                .map(h -> SubmissionHintItemResponse.builder()
                        .id(h.getId())
                        .hintNumber(h.getHintNumber())
                        .studentSnapshotContent(h.getStudentSnapshotContent())
                        .aiHintContent(h.getAiHintContent())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return HintHistoryResponse.builder()
                .submissionId(submissionId)
                .totalUsed(totalUsed)
                .maxHints(MAX_HINTS)
                .remainingHints(Math.max(0, MAX_HINTS - totalUsed))
                .hints(items)
                .build();
    }

    private String sanitizeContent(String content) {
        if (content == null) return "";
        String sanitized = content.replaceAll("(?s)<!-- DRAWINGS_DATA_START.*?DRAWINGS_DATA_END -->", "").trim();
        if (sanitized.length() > 3000) {
            sanitized = sanitized.substring(sanitized.length() - 3000);
        }
        return sanitized;
    }

    private String buildSocraticPrompt(Assignment assignment, String studentContent) {
        String title = assignment.getTitle() != null ? assignment.getTitle() : "Bài tập Toán";
        String problemContent = assignment.getContent() != null ? assignment.getContent() : "Không có nội dung chi tiết";
        String contentText = studentContent.isBlank() ? "[Học sinh chưa bắt đầu làm bài / Bài làm trống]" : studentContent;

        return String.format("""
                Bạn là một trợ lý giáo viên Toán học xuất sắc và kiên nhẫn.
                Nhiệm vụ của bạn là đưa ra 01 GỢI Ý TƯ DUY NGẮN (từ 50 đến 120 từ) định hướng BƯỚC TIẾP THEO bám sát chính xác bài toán dưới đây.

                [ĐỀ BÀI TOÁN CẦN GIẢI]:
                - Tiêu đề: %s
                - Nội dung đề bài: %s

                [TIẾN ĐỘ BÀI LÀM HIỆN TẠI CỦA HỌC SINH]:
                %s

                YÊU CẦU BẮT BUỘC:
                1. Đọc và phân tích kỹ bài toán cụ thể nêu ở trên.
                2. Phân tích tiến độ bài làm của học sinh:
                   - Nếu bài làm trống: hãy chỉ rõ giả thiết, phương pháp hoặc công thức đầu tiên học sinh cần áp dụng để bắt đầu.
                   - Nếu học sinh đã viết bài làm: hãy nhận xét ngắn gọn bước làm hiện tại và đưa ra câu hỏi gợi mở hoặc nhắc lại định lý/công thức cho bước kế tiếp.
                3. TUYỆT ĐỐI KHÔNG đưa ra lời giải hoàn chỉnh hoặc đáp số số học cuối cùng.
                4. Trả lời trực tiếp vào gợi ý bằng tiếng Việt, văn phong thân thiện, động viên. Dùng KaTeX cho công thức toán (ví dụ: $x^2 + 1$).
                """, title, problemContent, contentText);
    }
}
