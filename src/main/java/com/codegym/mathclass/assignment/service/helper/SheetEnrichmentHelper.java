package com.codegym.mathclass.assignment.service.helper;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.repository.SheetPublishedClassProjection;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Helper chuyên trách làm giàu dữ liệu (enrichment) cho danh sách {@link AssignmentSheetResponse}:
 * <ul>
 *     <li>Làm giàu trạng thái nộp bài của học sinh theo từng bài tập con và toàn bộ phiếu bài tập.</li>
 *     <li>Làm giàu danh sách mã lớp học đã được giáo viên giao phiếu bài tập đó.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SheetEnrichmentHelper {

    private final SubmissionRepository submissionRepository;
    private final AssignmentSheetRepository assignmentSheetRepository;

    /**
     * Enrich danh sách phiếu bài tập với trạng thái nộp bài của học sinh.
     *
     * <p>Thu thập tất cả assignmentIds trong trang, fetch submissions bằng một batch query duy nhất,
     * sau đó gán dữ liệu vào từng item và tính submission status cấp sheet.
     * Tránh bài toán N+1 queries so với cách gọi DB theo từng sheet/item.
     *
     * @param page      Trang phản hồi phiếu bài tập
     * @param studentId ID của học sinh
     */
    public void enrichPageForStudent(Page<AssignmentSheetResponse> page, long studentId) {
        List<Long> allAssignmentIds = page.getContent().stream()
                .filter(sheet -> sheet.getItems() != null)
                .flatMap(sheet -> sheet.getItems().stream())
                .map(AssignmentResponse::getId)
                .toList();

        if (allAssignmentIds.isEmpty()) {
            return;
        }

        Map<Long, Submission> submissionByAssignmentId = fetchSubmissionsByAssignmentIds(allAssignmentIds, studentId);

        for (AssignmentSheetResponse sheet : page.getContent()) {
            applySubmissionDataToSheetItems(sheet, submissionByAssignmentId);
            sheet.setSubmissionStatus(resolveSheetSubmissionStatus(sheet));
            applySheetSubmissionTimes(sheet);
        }
    }

    /**
     * Enrich danh sách phiếu bài tập của giáo viên với danh sách các lớp đã được giao.
     *
     * <p>Dùng một batch query để lấy {title → [classCode]} cho toàn bộ trang,
     * sau đó gán vào từng sheet. Tránh N queries cho N sheets trong trang.
     *
     * @param page      Trang phản hồi phiếu bài tập
     * @param teacherId ID của giáo viên
     */
    public void enrichPageForTeacher(Page<AssignmentSheetResponse> page, long teacherId) {
        List<String> titles = page.getContent().stream()
                .map(AssignmentSheetResponse::getTitle)
                .distinct()
                .toList();

        if (titles.isEmpty()) {
            return;
        }

        Map<String, List<String>> publishedCodesByTitle = fetchPublishedCodesByTitles(teacherId, titles);

        for (AssignmentSheetResponse sheet : page.getContent()) {
            List<String> codes = publishedCodesByTitle.getOrDefault(sheet.getTitle(), List.of());
            sheet.setPublishedClassCodes(codes);
        }
    }

    /**
     * Tính toán thời điểm nộp bài đầu tiên/gần nhất và thời điểm chấm điểm của phiếu.
     *
     * @param sheet Phiếu bài tập cần cập nhật thời gian
     */
    public void applySheetSubmissionTimes(AssignmentSheetResponse sheet) {
        if (sheet.getItems() == null || sheet.getItems().isEmpty()) {
            return;
        }

        LocalDateTime latestSubmit = sheet.getItems().stream()
                .map(AssignmentResponse::getSubmissionCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime latestUpdate = sheet.getItems().stream()
                .filter(item -> SubmissionStatus.GRADED.name().equals(item.getSubmissionStatus()))
                .map(AssignmentResponse::getSubmissionUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        sheet.setSubmissionCreatedAt(latestSubmit);
        sheet.setSubmissionUpdatedAt(latestUpdate);
    }

    /**
     * Tính submission status cấp sheet dựa trên trạng thái tất cả items.
     * <ul>
     *     <li>GRADED: tất cả items đều có submission GRADED.</li>
     *     <li>SUBMITTED: có ít nhất một item đã SUBMITTED/GRADED/LATE.</li>
     *     <li>null: còn lại (chưa nộp hoặc chỉ là nháp).</li>
     * </ul>
     *
     * @param sheet Phiếu bài tập cần xác định trạng thái nộp bài
     * @return Tên trạng thái nộp bài cấp sheet
     */
    public String resolveSheetSubmissionStatus(AssignmentSheetResponse sheet) {
        List<AssignmentResponse> items = sheet.getItems();
        if (items == null || items.isEmpty()) {
            return null;
        }

        boolean allGraded = items.stream()
                .allMatch(item -> SubmissionStatus.GRADED.name().equals(item.getSubmissionStatus()));
        if (allGraded) {
            return SubmissionStatus.GRADED.name();
        }

        boolean anySubmittedOrGraded = items.stream()
                .map(AssignmentResponse::getSubmissionStatus)
                .anyMatch(status -> SubmissionStatus.SUBMITTED.name().equals(status)
                        || SubmissionStatus.GRADED.name().equals(status)
                        || SubmissionStatus.LATE.name().equals(status));
        if (anySubmittedOrGraded) {
            return SubmissionStatus.SUBMITTED.name();
        }

        return null;
    }

    // ==========================================
    // Internal Helper Methods
    // ==========================================

    private Map<Long, Submission> fetchSubmissionsByAssignmentIds(List<Long> assignmentIds, long studentId) {
        List<Submission> submissions = submissionRepository
                .findAllByAssignmentIdInAndStudentId(assignmentIds, studentId);

        return submissions.stream()
                .collect(Collectors.toMap(
                        s -> s.getAssignment().getId(),
                        s -> s,
                        (existing, incoming) -> existing.getUpdatedAt().isAfter(incoming.getUpdatedAt())
                                ? existing
                                : incoming));
    }

    private void applySubmissionDataToSheetItems(
            AssignmentSheetResponse sheet, Map<Long, Submission> submissionByAssignmentId) {
        if (sheet.getItems() == null) {
            return;
        }

        for (AssignmentResponse item : sheet.getItems()) {
            Submission submission = submissionByAssignmentId.get(item.getId());
            if (submission == null) {
                continue;
            }

            item.setSubmissionStatus(submission.getStatus().name());
            item.setSubmissionCreatedAt(submission.getCreatedAt());
            item.setSubmissionUpdatedAt(submission.getUpdatedAt());
            item.setSubmissionScore(submission.getScore());
        }
    }

    private Map<String, List<String>> fetchPublishedCodesByTitles(long teacherId, List<String> titles) {
        List<SheetPublishedClassProjection> rows = assignmentSheetRepository
                .findTitleAndClassCodeByTeacherIdAndTitlesIn(teacherId, titles);

        Map<String, List<String>> result = new HashMap<>();
        for (SheetPublishedClassProjection row : rows) {
            result.computeIfAbsent(row.getTitle(), k -> new ArrayList<>()).add(row.getClassCode());
        }
        return result;
    }
}
